package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** Makes a binding conditional, {@code x = lib.mkIf cfg.enable value;}, the condition selected to be typed over. */
public final class NixWrapInMkIfIntention extends PsiElementBaseIntentionAction {
    /** Values that need no parentheses as an argument. */
    private static final Set<NodeKind> SIMPLE = Set.of(NodeKind.VAR, NodeKind.INT, NodeKind.FLOAT, NodeKind.URI,
            NodeKind.STRING, NodeKind.IND_STRING, NodeKind.PATH, NodeKind.SEARCH_PATH, NodeKind.PAREN, NodeKind.LIST,
            NodeKind.ATTR_SET, NodeKind.SELECT);

    @Override
    public @NotNull String getFamilyName() {
        return "Wrap in mkIf";
    }

    @Override
    public @NotNull String getText() {
        return getFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        ASTNode value = NixMkIf.valueAt(element);
        return value != null && NixMkIf.unwrapped(value) == null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        ASTNode value = NixMkIf.valueAt(element);
        if (value == null) {
            return;
        }
        PsiElement psi = value.getPsi();
        String function = NixScopes.resolve("lib", psi) == null && NixScopes.resolve("mkIf", psi) != null
                ? "mkIf" : "lib.mkIf";
        String condition = NixScopes.resolve("cfg", psi) != null ? "cfg.enable" : "condition";
        boolean simple = SIMPLE.stream().anyMatch(kind -> NixFormals.is(value, kind));
        String text = function + " " + condition + " " + (simple ? value.getText() : "(" + value.getText() + ")");
        int start = value.getStartOffset();
        editor.getDocument().replaceString(start, start + value.getTextLength(), text);
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        int conditionStart = start + function.length() + 1;
        editor.getCaretModel().moveToOffset(conditionStart + condition.length());
        editor.getSelectionModel().setSelection(conditionStart, conditionStart + condition.length());
    }
}
