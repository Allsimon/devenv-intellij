package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.parser.NodeKind;
import org.affogato.parser.Quotes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Rewrites a string in the other quoting, offered only where its value stays the same. */
public final class NixToggleQuotesIntention extends PsiElementBaseIntentionAction {
    @Override
    public @NotNull String getFamilyName() {
        return "Convert between \"...\" and ''...''";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        NixStringElement string = stringAt(element);
        if (string == null) {
            return false;
        }
        setText(string.isIndented() ? "Convert to \"...\"" : "Convert to ''...''");
        return Quotes.toggle(string.getText()) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        NixStringElement string = stringAt(element);
        String other = string == null ? null : Quotes.toggle(string.getText());
        if (other != null) {
            string.updateText(other);
        }
    }

    /** The string at the caret, but not one naming an attribute, which cannot be indented. */
    private static @Nullable NixStringElement stringAt(PsiElement element) {
        NixStringElement string = PsiTreeUtil.getParentOfType(element, NixStringElement.class, false);
        if (string == null) {
            return null;
        }
        var parent = string.getNode().getTreeParent();
        boolean name = NixFormals.is(parent, NodeKind.ATTR_PATH) || NixFormals.is(parent, NodeKind.INHERIT);
        return name ? null : string;
    }
}
