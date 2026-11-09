package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Keeps the language "Inject language or reference" picks as a {@code /* json *\/} comment before the string, which
 * {@link NixInjector} reads: the choice then lives in the source, not in the IDE's settings.
 */
public final class NixInjectionSupport extends AbstractLanguageInjectionSupport {
    @Override
    public @NotNull String getId() {
        return "nix";
    }

    @Override
    public Class<?> @NotNull [] getPatternClasses() {
        return new Class<?>[0];
    }

    @Override
    public boolean isApplicableTo(PsiLanguageInjectionHost host) {
        return host instanceof NixStringElement;
    }

    @Override
    public boolean useDefaultInjector(PsiLanguageInjectionHost host) {
        return false;
    }

    @Override
    public boolean addInjectionInPlace(Language language, PsiLanguageInjectionHost host) {
        if (!(host instanceof NixStringElement)) {
            return false;
        }
        String comment = "/* " + name(language) + " */";
        edit(host, document -> {
            ASTNode existing = NixInjector.languageComment(host.getNode());
            if (existing != null) {
                document.replaceString(existing.getStartOffset(), existing.getStartOffset() + existing.getTextLength(),
                        comment);
            } else {
                document.insertString(host.getTextRange().getStartOffset(), comment + " ");
            }
        });
        return true;
    }

    @Override
    public boolean removeInjectionInPlace(PsiLanguageInjectionHost host) {
        ASTNode comment = host instanceof NixStringElement ? NixInjector.languageComment(host.getNode()) : null;
        if (comment == null) {
            return false;
        }
        edit(host, document -> document.deleteString(comment.getStartOffset(), host.getTextRange().getStartOffset()));
        return true;
    }

    /** A name {@link NixInjector} resolves back to the language: its id, or its extension when the id has spaces. */
    private static String name(Language language) {
        String id = language.getID();
        if (id.matches("[\\w+#.-]+")) {
            return id.toLowerCase(Locale.ROOT);
        }
        var fileType = language.getAssociatedFileType();
        return fileType == null ? id.replace(' ', '-').toLowerCase(Locale.ROOT) : fileType.getDefaultExtension();
    }

    private static void edit(PsiLanguageInjectionHost host, java.util.function.Consumer<Document> change) {
        var project = host.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(host.getContainingFile());
        if (document == null) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, () -> {
            change.accept(document);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        });
    }
}
