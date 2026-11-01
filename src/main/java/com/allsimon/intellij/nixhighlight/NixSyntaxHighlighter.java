package com.allsimon.intellij.nixhighlight;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.graalnix.parser.Highlighter.Kind;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

final class NixSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey[]> KEYS = new HashMap<>();

    static {
        key(Kind.KEYWORD, "NIX_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
        key(Kind.STRING, "NIX_STRING", DefaultLanguageHighlighterColors.STRING);
        key(Kind.ESCAPE, "NIX_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
        key(Kind.INTERP, "NIX_INTERPOLATION", DefaultLanguageHighlighterColors.KEYWORD);
        key(Kind.COMMENT, "NIX_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
        key(Kind.CONSTANT, "NIX_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT);
        key(Kind.BUILTIN, "NIX_BUILTIN", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL);
        key(Kind.NUMBER, "NIX_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
        key(Kind.PATH, "NIX_PATH", DefaultLanguageHighlighterColors.STRING);
        key(Kind.OPERATOR, "NIX_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
        key(Kind.ATTR, "NIX_ATTRIBUTE", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
        key(Kind.PARAM, "NIX_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
    }

    private static void key(Kind kind, String name, TextAttributesKey fallback) {
        KEYS.put(NixTokenTypes.of(kind), pack(createTextAttributesKey(name, fallback)));
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
