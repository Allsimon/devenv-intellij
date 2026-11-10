package com.allsimon.intellij.nixlang;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import org.jetbrains.annotations.NotNull;

/** A path, whose segments refer to the files and directories relative to the file it is written in. */
public final class NixPathElement extends ASTWrapperPsiElement {
    NixPathElement(@NotNull ASTNode node) {
        super(node);
    }

    /** Whether the path is relative and has no interpolation, so that it names a file in the project. */
    boolean isProjectPath() {
        String text = getText();
        return getNode().getFirstChildNode() == getNode().getLastChildNode()
                && !text.startsWith("/") && !text.startsWith("~");
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        if (!isProjectPath()) {
            return PsiReference.EMPTY_ARRAY;
        }
        return new FileReferenceSet(getText(), this, 0, null, true).getAllReferences();
    }
}
