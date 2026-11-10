package com.allsimon.intellij.nixlang;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;

/** Counts the TODO patterns of comments for the TODO tool window. */
public final class NixTodoIndexer extends LexerBasedTodoIndexer {
    @Override
    public Lexer createLexer(OccurrenceConsumer consumer) {
        return new BaseFilterLexer(new NixLexer(), consumer) {
            @Override
            public void advance() {
                if (NixElementTypes.COMMENTS.contains(myDelegate.getTokenType())) {
                    advanceTodoItemCountsInToken();
                }
                myDelegate.advance();
            }
        };
    }
}
