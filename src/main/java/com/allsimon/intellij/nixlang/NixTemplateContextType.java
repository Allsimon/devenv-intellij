package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/** Nix code, outside strings and comments. */
public final class NixTemplateContextType extends TemplateContextType {
    public NixTemplateContextType() {
        super("Nix");
    }

    @Override
    public boolean isInContext(@NotNull TemplateActionContext context) {
        if (!(context.getFile() instanceof NixFile file)) {
            return false;
        }
        int offset = Math.max(0, context.getStartOffset() - 1);
        PsiElement element = file.findElementAt(offset);
        if (element == null || element instanceof PsiWhiteSpace) {
            return true;
        }
        var type = element.getNode().getElementType();
        return !NixElementTypes.COMMENTS.contains(type) && !NixElementTypes.STRING_CONTENT.contains(type)
                && PsiTreeUtil.getParentOfType(element, NixStringElement.class) == null;
    }
}
