package com.allsimon.intellij.nixlang;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.affogato.parser.Lexeme;
import org.affogato.parser.NodeKind;
import org.affogato.parser.Scopes;
import org.affogato.parser.SyntaxView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Nix's lexical scopes, as affogato's {@link Scopes} resolves them, over the PSI. */
final class NixScopes {
    /** The PSI as affogato's trees: the file is the root, whitespace and comments are left out. */
    static final SyntaxView<PsiElement> VIEW = new SyntaxView<>() {
        @Override
        public NodeKind kind(PsiElement element) {
            return NixElementTypes.nodeKind(element.getNode().getElementType());
        }

        @Override
        public Lexeme.Kind token(PsiElement element) {
            return NixElementTypes.kind(element.getNode().getElementType());
        }

        @Override
        public PsiElement parent(PsiElement element) {
            return element instanceof PsiFile ? null : element.getParent();
        }

        @Override
        public List<PsiElement> children(PsiElement element) {
            List<PsiElement> children = new ArrayList<>();
            for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof PsiWhiteSpace) && !(child instanceof PsiComment)) {
                    children.add(child);
                }
            }
            return children;
        }

        @Override
        public String text(PsiElement element) {
            return element.getText();
        }

        @Override
        public String commentBefore(PsiElement element) {
            PsiElement previous = element.getPrevSibling();
            while (previous instanceof PsiWhiteSpace) {
                previous = previous.getPrevSibling();
            }
            return previous instanceof PsiComment comment ? comment.getText() : null;
        }
    };

    private NixScopes() {
    }

    /** The definition of {@code name} visible from {@code from}, or null. */
    static @Nullable NixNamedElement resolve(@NotNull String name, @NotNull PsiElement from) {
        return Scopes.resolve(VIEW, name, from) instanceof NixNamedElement named ? named : null;
    }

    /** Every definition visible from {@code from}, the innermost hiding the outer ones of the same name. */
    static List<NixNamedElement> visible(@NotNull PsiElement from) {
        return Scopes.visible(VIEW, from).stream()
                .filter(NixNamedElement.class::isInstance)
                .map(NixNamedElement.class::cast)
                .toList();
    }

    /** Where the name of an {@code inherit} without a source is looked up from, or null for another name. */
    static @Nullable PsiElement inheritedFrom(@NotNull PsiElement name) {
        return Scopes.inheritedFrom(VIEW, name);
    }
}
