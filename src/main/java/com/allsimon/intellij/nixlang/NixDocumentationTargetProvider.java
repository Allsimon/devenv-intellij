package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider;
import com.intellij.psi.PsiFile;
import org.affogato.lsp.Analysis;
import org.affogato.lsp.Analysis.Completion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The documentation of the nixpkgs attributes and options under the caret, and of their completions. */
public final class NixDocumentationTargetProvider
        implements DocumentationTargetProvider, LookupElementDocumentationTargetProvider {
    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        if (!(file instanceof NixFile)) {
            return List.of();
        }
        Analysis.Hover hover = NixAnalyses.of(file).hover(offset);
        if (hover == null) {
            return List.of();
        }
        String name = file.getText().substring(hover.start(), hover.end());
        return List.of(new NixDocumentationTarget(file.getProject(), name, hover.markdown()));
    }

    @Override
    public @Nullable DocumentationTarget documentationTarget(
            @NotNull PsiFile file, @NotNull LookupElement element, int offset) {
        if (!(file instanceof NixFile) || !(element.getObject() instanceof Completion completion) || completion.path() == null) {
            return null;
        }
        Analysis analysis = NixAnalyses.of(file);
        return NixDocumentationTarget.of(file.getProject(), completion.label(), analysis.details(completion));
    }
}
