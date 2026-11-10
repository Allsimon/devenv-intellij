package com.allsimon.intellij.nixlang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a name lexically from {@code from}. It is soft: a name a {@code with} brings, a builtin, or an undefined
 * one stays unresolved without an error, which the linter reports when it is one.
 */
final class NixVarReference extends PsiReferenceBase<PsiElement> {
    private final PsiElement from;

    NixVarReference(@NotNull PsiElement element, @NotNull PsiElement from) {
        super(element, TextRange.from(0, element.getTextLength()), true);
        this.from = from;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return NixScopes.resolve(getElement().getText(), from);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        NixVarElement.replaceIdentifier(getElement(), newElementName);
        return getElement();
    }
}
