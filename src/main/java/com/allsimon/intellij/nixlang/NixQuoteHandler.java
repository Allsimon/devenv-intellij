package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import org.affogato.parser.Lexeme.Kind;

/** Pairs {@code "}; a string counts as unclosed when its closing quote isn't on its first line. */
public final class NixQuoteHandler implements QuoteHandler {
    @Override
    public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
        return kind(iterator) == Kind.STR_CLOSE;
    }

    @Override
    public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
        return kind(iterator) == Kind.STR_OPEN && offset == iterator.getStart();
    }

    @Override
    public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
        var document = editor.getDocument();
        int lineEnd = document.getLineEndOffset(document.getLineNumber(iterator.getStart()));
        int steps = 0;
        boolean closed = false;
        while (!iterator.atEnd() && iterator.getStart() < lineEnd) {
            if (steps > 0 && kind(iterator) == Kind.STR_CLOSE) {
                closed = true;
                break;
            }
            iterator.advance();
            steps++;
        }
        for (; steps > 0; steps--) {
            iterator.retreat();
        }
        return !closed;
    }

    @Override
    public boolean isInsideLiteral(HighlighterIterator iterator) {
        Kind kind = kind(iterator);
        return kind == Kind.STR_TEXT || kind == Kind.STR_ESCAPE;
    }

    private static Kind kind(HighlighterIterator iterator) {
        return iterator.atEnd() ? null : NixElementTypes.kind(iterator.getTokenType());
    }
}
