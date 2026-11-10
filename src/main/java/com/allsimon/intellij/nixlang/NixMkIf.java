package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class NixMkIf {
    private NixMkIf() {
    }

    /** The value of the binding whose path or {@code =} the element is in. */
    static @Nullable ASTNode valueAt(PsiElement element) {
        if (element instanceof PsiWhiteSpace) {
            element = PsiTreeUtil.prevLeaf(element);
        }
        for (PsiElement e = element; e != null; e = e.getParent()) {
            ASTNode node = e.getNode();
            if (NixFormals.is(node, NodeKind.BINDING)) {
                List<ASTNode> parts = NixFormals.children(node);
                for (int i = 0; i + 1 < parts.size(); i++) {
                    if (NixElementTypes.kind(parts.get(i).getElementType()) == Kind.ASSIGN) {
                        boolean onValue = PsiTreeUtil.isAncestor(parts.get(i + 1).getPsi(), element, false);
                        return onValue ? null : parts.get(i + 1);
                    }
                }
                return null;
            }
        }
        return null;
    }

    /** The value inside {@code mkIf condition value}, its parentheses dropped, or null for another value. */
    static @Nullable String unwrapped(ASTNode value) {
        if (!NixFormals.is(value, NodeKind.APPLY)) {
            return null;
        }
        List<ASTNode> parts = NixFormals.children(value);
        ASTNode function = parts.getFirst();
        boolean mkIf = function.getText().equals("mkIf") || function.getText().endsWith(".mkIf");
        if (!mkIf || parts.size() != 3) {
            return null;
        }
        ASTNode inner = parts.get(2);
        if (NixFormals.is(inner, NodeKind.PAREN)) {
            List<ASTNode> paren = NixFormals.children(inner);
            return paren.size() == 3 ? paren.get(1).getText() : inner.getText();
        }
        return inner.getText();
    }
}
