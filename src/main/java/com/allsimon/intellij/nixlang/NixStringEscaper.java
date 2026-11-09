package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.affogato.parser.Lexeme.Kind;
import org.jetbrains.annotations.NotNull;

/** Decodes the escapes of a string; the text of an interpolation stays as written. */
final class NixStringEscaper extends LiteralTextEscaper<NixStringElement> {
    /** For each decoded character, its offset in the host, then the end of the decoded range. */
    private final IntArrayList offsets = new IntArrayList();

    NixStringEscaper(@NotNull NixStringElement host) {
        super(host);
    }

    @Override
    public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
        offsets.clear();
        String text = myHost.getText();
        for (ASTNode child = myHost.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            int start = child.getStartOffsetInParent();
            int end = start + child.getTextLength();
            if (end <= rangeInsideHost.getStartOffset() || start >= rangeInsideHost.getEndOffset()) {
                continue;
            }
            if (NixElementTypes.kind(child.getElementType()) == Kind.STR_ESCAPE) {
                if (!rangeInsideHost.contains(new TextRange(start, end))) {
                    return false;
                }
                for (char c : unescape(child.getText(), myHost.isIndented()).toCharArray()) {
                    outChars.append(c);
                    offsets.add(start);
                }
                continue;
            }
            int from = Math.max(start, rangeInsideHost.getStartOffset());
            int to = Math.min(end, rangeInsideHost.getEndOffset());
            for (int i = from; i < to; i++) {
                outChars.append(text.charAt(i));
                offsets.add(i);
            }
        }
        offsets.add(rangeInsideHost.getEndOffset());
        return true;
    }

    static String unescape(String escape, boolean indented) {
        String rest = indented ? escape.substring(2) : escape;
        if (indented && !rest.startsWith("\\")) {
            return rest.equals("'") ? "''" : rest;
        }
        return switch (rest.charAt(1)) {
            case 'n' -> "\n";
            case 'r' -> "\r";
            case 't' -> "\t";
            default -> String.valueOf(rest.charAt(1));
        };
    }

    @Override
    public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
        return offsetInDecoded >= 0 && offsetInDecoded < offsets.size() ? offsets.getInt(offsetInDecoded) : -1;
    }

    @Override
    public @NotNull TextRange getRelevantTextRange() {
        return myHost.contentRange();
    }

    @Override
    public boolean isOneLine() {
        return false;
    }
}
