package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.affogato.lsp.Analysis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Quotes an attribute name, or unquotes one that is a plain name. */
public final class NixAttributeQuotesIntention extends PsiElementBaseIntentionAction {
    @Override
    public @NotNull String getFamilyName() {
        return "Quote or unquote the attribute name";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Analysis.Rewrite rewrite = rewrite(editor, element);
        if (rewrite == null) {
            return false;
        }
        setText(rewrite.title());
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Analysis.Rewrite rewrite = rewrite(editor, element);
        if (rewrite != null) {
            editor.getDocument().replaceString(rewrite.start(), rewrite.end(), rewrite.text());
        }
    }

    private static @Nullable Analysis.Rewrite rewrite(Editor editor, PsiElement element) {
        return element.getContainingFile() instanceof NixFile file
                ? NixAnalyses.syntax(file).attributeQuotes(editor.getCaretModel().getOffset())
                : null;
    }
}
