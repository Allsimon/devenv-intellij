package com.allsimon.intellij.nixlang;

import com.allsimon.intellij.nixhighlight.NixColors;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.affogato.parser.Highlighter;
import org.affogato.parser.Lexeme.Kind;
import org.jetbrains.annotations.NotNull;

/** Colors the tokens; {@link NixAnnotator} adds what takes the tree, such as attribute names. */
public final class NixHighlighter extends SyntaxHighlighterBase {
    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new NixLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        Kind kind = NixElementTypes.kind(tokenType);
        Highlighter.Kind color = kind == null ? null : color(kind);
        return color == null ? TextAttributesKey.EMPTY_ARRAY : pack(NixColors.of(color));
    }

    private static Highlighter.Kind color(Kind kind) {
        return switch (kind) {
            case ID, WHITESPACE, BAD_CHARACTER -> null;
            case INT, FLOAT -> Highlighter.Kind.NUMBER;
            case PATH, HPATH, SPATH, URI, PATH_TEXT -> Highlighter.Kind.PATH;
            case STR_OPEN, IND_STR_OPEN, STR_CLOSE, IND_STR_CLOSE, STR_TEXT -> Highlighter.Kind.STRING;
            case STR_ESCAPE -> Highlighter.Kind.ESCAPE;
            case DOLLAR_CURLY -> Highlighter.Kind.INTERP;
            case COMMENT -> Highlighter.Kind.COMMENT;
            case IF, THEN, ELSE, ASSERT, WITH, LET, IN, REC, INHERIT, OR_KW -> Highlighter.Kind.KEYWORD;
            default -> Highlighter.Kind.OPERATOR;
        };
    }
}
