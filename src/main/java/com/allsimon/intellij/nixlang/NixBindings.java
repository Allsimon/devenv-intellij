package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class NixBindings {
    private NixBindings() {
    }

    static boolean isBinding(@NotNull PsiElement element) {
        return element.getNode().getElementType() == NixElementTypes.of(NodeKind.BINDING);
    }

    /** The attribute path a binding sets, as written. */
    static @NotNull String name(@NotNull PsiElement binding) {
        ASTNode path = binding.getNode().findChildByType(NixElementTypes.of(NodeKind.ATTR_PATH));
        return path == null ? "" : path.getText();
    }

    /** The nearest bindings below an element, whatever expression they sit in. */
    static @NotNull List<PsiElement> nested(@NotNull PsiElement element) {
        List<PsiElement> bindings = new ArrayList<>();
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (isBinding(child)) {
                bindings.add(child);
            } else {
                bindings.addAll(nested(child));
            }
        }
        return bindings;
    }
}
