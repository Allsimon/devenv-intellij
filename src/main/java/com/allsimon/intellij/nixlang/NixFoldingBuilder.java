package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.tree.IElementType;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Folds the attribute sets, lists, {@code let} blocks, indented strings and block comments that span lines. */
public final class NixFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document,
                                                          boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        for (PsiElement element : SyntaxTraverser.psiTraverser(root)) {
            ASTNode node = element.getNode();
            TextRange range = node.getTextRange();
            if (placeholder(node) != null && !range.isEmpty()
                    && document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset())) {
                descriptors.add(new FoldingDescriptor(node, range));
            }
        }
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return placeholder(node);
    }

    private static @Nullable String placeholder(ASTNode node) {
        IElementType type = node.getElementType();
        if (type == NixElementTypes.of(NodeKind.ATTR_SET)) {
            return node.getFirstChildNode().getElementType() == NixElementTypes.of(Kind.LBRACE) ? "{...}"
                    : node.getFirstChildNode().getText() + " {...}";
        }
        if (type == NixElementTypes.of(NodeKind.LIST)) {
            return "[...]";
        }
        if (type == NixElementTypes.of(NodeKind.LET)) {
            return "let ... in";
        }
        if (type == NixElementTypes.of(NodeKind.IND_STRING)) {
            return "''...''";
        }
        if (type == NixElementTypes.of(Kind.COMMENT) && node.getText().startsWith("/*")) {
            return "/*...*/";
        }
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
