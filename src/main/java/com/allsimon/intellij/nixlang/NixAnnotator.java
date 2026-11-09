package com.allsimon.intellij.nixlang;

import com.allsimon.intellij.nixhighlight.NixColors;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.affogato.parser.Highlighter;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;

/** Colors what the tokens alone don't tell: attribute names, parameters, builtins and interpolation braces. */
public final class NixAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        ASTNode node = element.getNode();
        IElementType type = node.getElementType();
        ASTNode parent = node.getTreeParent();
        Highlighter.Kind kind = null;
        if (type == NixElementTypes.of(NodeKind.ATTR_NAME)) {
            kind = Highlighter.Kind.ATTR;
        } else if (type == NixElementTypes.of(NodeKind.PARAM)) {
            kind = Highlighter.Kind.PARAM;
        } else if (type == NixElementTypes.of(Kind.ID) && is(parent, NodeKind.FORMAL)) {
            kind = Highlighter.Kind.PARAM;
        } else if (type == NixElementTypes.of(NodeKind.VAR)) {
            kind = Highlighter.nameKind(node.getText());
        } else if (type == NixElementTypes.of(Kind.RBRACE)
                && (is(parent, NodeKind.INTERPOLATION) || is(parent, NodeKind.DYNAMIC_ATTR))) {
            kind = Highlighter.Kind.INTERP;
        }
        if (kind != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(NixColors.of(kind)).create();
        }
    }

    private static boolean is(ASTNode node, NodeKind kind) {
        return node != null && node.getElementType() == NixElementTypes.of(kind);
    }
}
