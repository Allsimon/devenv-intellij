package com.allsimon.intellij.nixlang;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.affogato.parser.Lexeme;
import org.affogato.parser.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Affogato's {@link Tokenizer} over the whole range at once. */
final class NixLexer extends LexerBase {
    private CharSequence buffer = "";
    private int startOffset;
    private int endOffset;
    private List<Lexeme> lexemes = List.of();
    private int index;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.lexemes = Tokenizer.tokenize(buffer.subSequence(startOffset, endOffset).toString());
        this.index = 0;
    }

    /** 0 where the editor can restart lexing after a change: at a top-level lexeme, outside strings and paths. */
    @Override
    public int getState() {
        return index >= lexemes.size() || lexemes.get(index).topLevel() ? 0 : 1;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return index < lexemes.size() ? NixElementTypes.of(lexemes.get(index).kind()) : null;
    }

    @Override
    public int getTokenStart() {
        return index < lexemes.size() ? startOffset + lexemes.get(index).start() : endOffset;
    }

    @Override
    public int getTokenEnd() {
        return index < lexemes.size() ? startOffset + lexemes.get(index).end() : endOffset;
    }

    @Override
    public void advance() {
        index++;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }
}
