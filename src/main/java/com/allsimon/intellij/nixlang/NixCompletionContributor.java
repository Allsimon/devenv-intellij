package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiFile;
import org.affogato.lsp.Analysis;
import org.affogato.lsp.Analysis.Completion;
import org.affogato.lsp.Evaluator;
import org.affogato.parser.Lexeme.Kind;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.EnumSet;
import java.util.Set;

/**
 * affogato's completion: the names in scope, the attributes of local sets and of nixpkgs, NixOS and devenv options,
 * flake inputs and outputs, builtins and keywords.
 */
public final class NixCompletionContributor extends CompletionContributor {
    /** Where a name is not typed: in paths, URIs, string text and comments. */
    private static final Set<Kind> OUTSIDE = EnumSet.of(
            Kind.PATH, Kind.HPATH, Kind.SPATH, Kind.URI, Kind.STR_TEXT, Kind.STR_ESCAPE, Kind.COMMENT);

    /** How long completion waits for nixpkgs to be evaluated, since none of its names come before. */
    private static final long WAIT_NANOS = 10_000_000_000L;

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiFile file = parameters.getOriginalFile();
        if (!(file instanceof NixFile)) {
            return;
        }
        int offset = parameters.getOffset();
        String text = file.getText();
        Kind at = NixElementTypes.kind(parameters.getPosition().getNode().getElementType());
        if (at != null && OUTSIDE.contains(at)) {
            return;
        }
        int start = offset;
        while (start > 0 && isNameChar(text.charAt(start - 1))) {
            start--;
        }
        CompletionResultSet names = result.withPrefixMatcher(text.substring(start, offset));

        Analysis analysis = NixAnalyses.of(file);
        Analysis.Completions completions = analysis.completion(offset, false);
        long deadline = System.nanoTime() + WAIT_NANOS;
        while (completions.incomplete() && completions.items().isEmpty() && System.nanoTime() < deadline) {
            ProgressManager.checkCanceled();
            completions = analysis.completion(offset, false);
        }
        if (completions.incomplete()) {
            names.restartCompletionOnAnyPrefixChange();
        }
        for (Completion completion : completions.items()) {
            names.addElement(lookup(analysis, completion));
        }
    }

    private static LookupElement lookup(Analysis analysis, Completion completion) {
        LookupElementBuilder builder = LookupElementBuilder.create(completion, completion.label())
                .withIcon(icon(completion.kind()))
                .withTypeText(completion.detail())
                .withBoldness(completion.kind() == Analysis.Kind.KEYWORD);
        if (completion.path() == null) {
            return builder;
        }
        return builder.withExpensiveRenderer(new LookupElementRenderer<>() {
            @Override
            public void renderElement(LookupElement element, LookupElementPresentation presentation) {
                presentation.setItemText(completion.label());
                presentation.setIcon(icon(completion.kind()));
                Evaluator.Attribute a = analysis.details(completion);
                if (a != null && a.type() != null) {
                    presentation.setTypeText(detail(a));
                }
            }
        });
    }

    static String detail(Evaluator.Attribute a) {
        return a.version() != null ? a.version() : a.type().equals("option") ? a.value() : a.type();
    }

    private static Icon icon(Analysis.Kind kind) {
        return switch (kind) {
            case VARIABLE -> AllIcons.Nodes.Variable;
            case ATTRIBUTE -> AllIcons.Nodes.Field;
            case PROPERTY -> AllIcons.Nodes.Property;
            case BUILTIN -> AllIcons.Nodes.Function;
            case CONSTANT -> AllIcons.Nodes.Constant;
            case KEYWORD, SNIPPET -> null;
        };
    }

    static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\'' || c == '-';
    }
}
