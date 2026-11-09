package com.allsimon.intellij.nixhighlight;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.affogato.parser.Highlighter.Kind;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class NixSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey[]> KEYS = new HashMap<>();

    static {
        for (Kind kind : Kind.values()) {
            KEYS.put(NixTokenTypes.of(kind), pack(NixColors.of(kind)));
        }
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new NixHighlightingLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return KEYS.getOrDefault(tokenType, TextAttributesKey.EMPTY_ARRAY);
    }
}
