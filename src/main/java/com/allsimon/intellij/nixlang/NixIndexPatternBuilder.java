package com.allsimon.intellij.nixlang;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Finds TODO patterns in comments. */
public final class NixIndexPatternBuilder implements IndexPatternBuilder {
    @Override
    public @Nullable Lexer getIndexingLexer(@NotNull PsiFile file) {
        return file instanceof NixFile ? new NixLexer() : null;
    }

    @Override
    public @Nullable TokenSet getCommentTokenSet(@NotNull PsiFile file) {
        return file instanceof NixFile ? NixElementTypes.COMMENTS : null;
    }

    @Override
    public int getCommentStartDelta(IElementType tokenType) {
        return 0;
    }

    @Override
    public int getCommentEndDelta(IElementType tokenType) {
        return 0;
    }
}
