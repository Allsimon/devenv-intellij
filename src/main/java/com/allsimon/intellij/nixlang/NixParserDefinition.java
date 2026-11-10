package com.allsimon.intellij.nixlang;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.affogato.parser.NodeKind;
import org.affogato.parser.TreeParser;
import org.jetbrains.annotations.NotNull;

public final class NixParserDefinition implements ParserDefinition {
    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new NixLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            var file = builder.mark();
            TreeParser.parse(new PsiTreeBuilder(builder));
            file.done(root);
            return builder.getTreeBuilt();
        };
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return NixElementTypes.FILE;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return NixElementTypes.COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return NixElementTypes.STRING_CONTENT;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        if (NixElementTypes.STRINGS.contains(node.getElementType())) {
            return new NixStringElement(node);
        }
        if (NixElementTypes.NAMES.contains(node.getElementType())) {
            return new NixNamedElement(node);
        }
        if (node.getElementType() == NixElementTypes.of(NodeKind.PATH)) {
            return new NixPathElement(node);
        }
        return node.getElementType() == NixElementTypes.of(NodeKind.VAR)
                ? new NixVarElement(node)
                : new ASTWrapperPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new NixFile(viewProvider);
    }
}
