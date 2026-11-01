package com.allsimon.intellij.nixhighlight;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.graalnix.parser.Highlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns graalnix's highlighting spans into the contiguous tokens an editor highlighter wants, plain text filling
 * the gaps.
 * <p>
 * The spans depend on what follows them - {@code a} is an attribute name only once {@code =} comes - so the whole
 * text is classified on every start, and only the first token reports the initial state: the editor then restarts
 * from the top after each change, instead of from a token whose class the change may have altered.
 */
final class NixHighlightingLexer extends LexerBase {
    private CharSequence buffer = "";
    private int bufferEnd;
    private final List<Token> tokens = new ArrayList<>();
    private int index;

    private record Token(int start, int end, IElementType type) {
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        tokens.clear();
        int at = 0;
        for (Highlighter.Span span : Highlighter.highlight(buffer.subSequence(0, endOffset).toString())) {
            if (at < span.start()) {
                tokens.add(new Token(at, span.start(), NixTokenTypes.PLAIN));
            }
            tokens.add(new Token(span.start(), span.end(), NixTokenTypes.of(span.kind())));
            at = span.end();
        }
        if (at < endOffset) {
            tokens.add(new Token(at, endOffset, NixTokenTypes.PLAIN));
        }

        index = 0;
        while (index < tokens.size() && tokens.get(index).end() <= startOffset) {
            index++;
        }
        if (index < tokens.size() && tokens.get(index).start() < startOffset) {
            Token token = tokens.get(index);
            tokens.set(index, new Token(startOffset, token.end(), token.type()));
        }
    }

    @Override
    public int getState() {
        return index < tokens.size() && tokens.get(index).start() == 0 ? 0 : 1;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return index < tokens.size() ? tokens.get(index).type() : null;
    }

    @Override
    public int getTokenStart() {
        return index < tokens.size() ? tokens.get(index).start() : bufferEnd;
    }

    @Override
    public int getTokenEnd() {
        return index < tokens.size() ? tokens.get(index).end() : bufferEnd;
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
        return bufferEnd;
    }
}
