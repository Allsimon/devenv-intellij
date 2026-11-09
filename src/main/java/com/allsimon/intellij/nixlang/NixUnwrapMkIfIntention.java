package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/** Makes a {@code mkIf} binding unconditional, keeping its value. */
public final class NixUnwrapMkIfIntention extends PsiElementBaseIntentionAction {
    @Override
    public @NotNull String getFamilyName() {
        return "Remove mkIf";
    }

    @Override
    public @NotNull String getText() {
        return getFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        ASTNode value = NixMkIf.valueAt(element);
        return value != null && NixMkIf.unwrapped(value) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        ASTNode value = NixMkIf.valueAt(element);
        String unwrapped = value == null ? null : NixMkIf.unwrapped(value);
        if (unwrapped != null) {
            editor.getDocument().replaceString(value.getStartOffset(), value.getStartOffset() + value.getTextLength(),
                    unwrapped);
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        }
    }
}
