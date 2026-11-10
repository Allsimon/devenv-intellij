package com.allsimon.intellij.nixlang;

import com.intellij.lang.PsiBuilder;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.affogato.parser.TreeBuilder;

/** Lets affogato's {@code TreeParser} drive a {@link PsiBuilder}. */
record PsiTreeBuilder(PsiBuilder builder) implements TreeBuilder {
    @Override
    public Kind token() {
        return NixElementTypes.kind(builder.getTokenType());
    }

    @Override
    public Kind lookAhead(int steps) {
        return NixElementTypes.kind(builder.lookAhead(steps));
    }

    @Override
    public void advance() {
        builder.advanceLexer();
    }

    @Override
    public Marker mark() {
        return new PsiMarker(builder.mark());
    }

    @Override
    public void error(String message) {
        builder.error(message);
    }

    private record PsiMarker(PsiBuilder.Marker marker) implements Marker {
        @Override
        public Marker precede() {
            return new PsiMarker(marker.precede());
        }

        @Override
        public void done(NodeKind kind) {
            marker.done(NixElementTypes.of(kind));
        }

        @Override
        public void error(String message) {
            marker.error(message);
        }

        @Override
        public void drop() {
            marker.drop();
        }
    }
}
