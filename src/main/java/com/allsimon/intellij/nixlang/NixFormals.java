package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The formals a set passed to a function fills: {@code { a, b ? 1 }} for {@code f { }} or {@code callPackage ./f.nix { }}. */
final class NixFormals {
    /** A formal's name, and whether it has a default. */
    record Formal(String name, boolean optional) {
        @Override
        public String toString() {
            return optional ? name + " ?" : name;
        }
    }

    private NixFormals() {
    }

    /** The formals of the function {@code set} is passed to, or null when it is not passed to a known one. */
    static @Nullable List<Formal> of(@NotNull PsiElement set) {
        ASTNode apply = set.getNode().getTreeParent();
        if (!is(set.getNode(), NodeKind.ATTR_SET) || !is(apply, NodeKind.APPLY)) {
            return null;
        }
        List<ASTNode> parts = children(apply);
        int index = parts.indexOf(set.getNode());
        ASTNode function = parts.getFirst();
        if (index == 1 && function.getPsi() instanceof NixVarElement var) {
            PsiElement definition = var.getReference().resolve();
            return definition == null ? null : formals(boundValue(definition.getNode()));
        }
        if (index == 2 && function.getText().endsWith("callPackage") && parts.get(1).getPsi() instanceof NixPathElement path) {
            PsiReference[] references = path.getReferences();
            PsiElement target = references.length == 0 ? null : references[references.length - 1].resolve();
            if (target instanceof PsiDirectory directory) {
                target = directory.findFile("default.nix");
            }
            if (target instanceof PsiFile file) {
                ASTNode root = file.getNode() == null ? null : children(file.getNode()).stream().findFirst().orElse(null);
                return formals(root);
            }
        }
        return null;
    }

    /** The value a binding gives its first name, as in {@code f = { a }: a;}. */
    private static @Nullable ASTNode boundValue(ASTNode name) {
        ASTNode path = name.getTreeParent();
        ASTNode binding = path == null ? null : path.getTreeParent();
        if (!is(path, NodeKind.ATTR_PATH) || !is(binding, NodeKind.BINDING) || children(path).size() != 1) {
            return null;
        }
        List<ASTNode> parts = children(binding);
        int assign = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (NixElementTypes.kind(parts.get(i).getElementType()) == Kind.ASSIGN) {
                assign = i;
            }
        }
        return assign >= 0 && assign + 1 < parts.size() ? parts.get(assign + 1) : null;
    }

    private static @Nullable List<Formal> formals(@Nullable ASTNode lambda) {
        ASTNode formals = is(lambda, NodeKind.LAMBDA)
                ? lambda.findChildByType(NixElementTypes.of(NodeKind.FORMALS)) : null;
        if (formals == null) {
            return null;
        }
        List<Formal> list = new ArrayList<>();
        for (ASTNode formal : children(formals)) {
            if (formal.getPsi() instanceof NixNamedElement named && named.getName() != null) {
                list.add(new Formal(named.getName(),
                        formal.findChildByType(NixElementTypes.of(Kind.QUESTION)) != null));
            }
        }
        return list;
    }

    static List<ASTNode> children(ASTNode node) {
        List<ASTNode> children = new ArrayList<>();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (!(child.getPsi() instanceof PsiWhiteSpace) && !NixElementTypes.COMMENTS.contains(child.getElementType())) {
                children.add(child);
            }
        }
        return children;
    }

    /** Whether a node is one of affogato's, as opposed to a token. */
    static boolean isExpression(ASTNode node) {
        for (NodeKind kind : NodeKind.values()) {
            if (node.getElementType() == NixElementTypes.of(kind)) {
                return true;
            }
        }
        return false;
    }

    static boolean is(@Nullable ASTNode node, NodeKind kind) {
        return node != null && node.getElementType() == NixElementTypes.of(kind);
    }
}
