package com.allsimon.intellij.nixlang;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An attribute name, a lambda parameter or a formal: what a variable can resolve to. A name an {@code inherit}
 * without a source copies refers in turn to the variable it copies.
 */
public final class NixNamedElement extends ASTWrapperPsiElement implements PsiNameIdentifierOwner {
    NixNamedElement(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        ASTNode id = getNode().getFirstChildNode();
        return id == null ? null : id.getPsi();
    }

    @Override
    public String getName() {
        PsiElement id = getNameIdentifier();
        return id == null ? null : id.getText();
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        NixVarElement.replaceIdentifier(this, name);
        return this;
    }

    @Override
    public int getTextOffset() {
        PsiElement id = getNameIdentifier();
        return id == null ? super.getTextOffset() : id.getTextOffset();
    }

    @Override
    public @Nullable PsiReference getReference() {
        PsiElement from = NixScopes.inheritedFrom(this);
        return from == null ? null : new NixVarReference(this, from);
    }
}
