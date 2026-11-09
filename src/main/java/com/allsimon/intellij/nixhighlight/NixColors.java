package com.allsimon.intellij.nixhighlight;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.affogato.parser.Highlighter.Kind;

import java.util.EnumMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class NixColors {
    private static final Map<Kind, TextAttributesKey> KEYS = new EnumMap<>(Kind.class);

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

    private NixColors() {
    }

    private static void key(Kind kind, String name, TextAttributesKey fallback) {
        KEYS.put(kind, createTextAttributesKey(name, fallback));
    }

    public static TextAttributesKey of(Kind kind) {
        return KEYS.get(kind);
    }
}
