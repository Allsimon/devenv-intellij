package com.allsimon.intellij.nixlang;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.affogato.parser.Lexeme.Kind;
import org.jetbrains.annotations.NotNull;

/** A variable, which refers to the binding, parameter or formal that defines it. */
public final class NixVarElement extends ASTWrapperPsiElement {
    NixVarElement(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public PsiReference getReference() {
        return new NixVarReference(this, this);
    }

    /** Replaces the identifier that starts {@code element} with {@code name}. */
    static void replaceIdentifier(PsiElement element, String name) {
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_'-]*")) {
            throw new IncorrectOperationException("'" + name + "' is not a Nix identifier");
        }
        var file = PsiFileFactory.getInstance(element.getProject())
                .createFileFromText("dummy.nix", NixFileType.INSTANCE, name);
        NixVarElement parsed = PsiTreeUtil.findChildOfType(file, NixVarElement.class);
        ASTNode id = element.getNode().getFirstChildNode();
        if (parsed == null || id == null || NixElementTypes.kind(id.getElementType()) != Kind.ID) {
            throw new IncorrectOperationException("cannot rename " + element.getText());
        }
        element.getNode().replaceChild(id, parsed.getNode().getFirstChildNode().copyElement());
    }
}
