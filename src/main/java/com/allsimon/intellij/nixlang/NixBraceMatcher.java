package com.allsimon.intellij.nixlang;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.affogato.parser.Lexeme.Kind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NixBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] PAIRS = {
            new BracePair(NixElementTypes.of(Kind.LBRACE), NixElementTypes.of(Kind.RBRACE), true),
            new BracePair(NixElementTypes.of(Kind.DOLLAR_CURLY), NixElementTypes.of(Kind.RBRACE), false),
            new BracePair(NixElementTypes.of(Kind.LBRACK), NixElementTypes.of(Kind.RBRACK), false),
            new BracePair(NixElementTypes.of(Kind.LPAREN), NixElementTypes.of(Kind.RPAREN), false),
    };

    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
