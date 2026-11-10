package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.affogato.parser.Bindings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/** Applies one of affogato's {@link Bindings} rewrites to the binding at the caret. */
abstract class NixBindingIntention extends PsiElementBaseIntentionAction {
    private final String name;
    private final BiFunction<String, Integer, Bindings.Rewrite> rewrite;

    NixBindingIntention(String name, BiFunction<String, Integer, Bindings.Rewrite> rewrite) {
        this.name = name;
        this.rewrite = rewrite;
        setText(name);
    }

    @Override
    public @NotNull String getFamilyName() {
        return name;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return rewriteAt(editor, element) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Bindings.Rewrite at = rewriteAt(editor, element);
        if (at != null) {
            editor.getDocument().replaceString(at.start(), at.end(), at.text());
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        }
    }

    private @Nullable Bindings.Rewrite rewriteAt(Editor editor, PsiElement element) {
        return element.getContainingFile() instanceof NixFile
                ? rewrite.apply(editor.getDocument().getText(), editor.getCaretModel().getOffset())
                : null;
    }
}
