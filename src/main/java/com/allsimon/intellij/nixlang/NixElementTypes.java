package com.allsimon.intellij.nixlang;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** The element types of affogato's lexemes and nodes. */
public final class NixElementTypes {
    public static final IFileElementType FILE = new IFileElementType(NixLanguage.INSTANCE);

    private static final Map<Kind, IElementType> TOKENS = new EnumMap<>(Kind.class);
    private static final Map<IElementType, Kind> KINDS = new HashMap<>();
    private static final Map<NodeKind, IElementType> NODES = new EnumMap<>(NodeKind.class);
    private static final Map<IElementType, NodeKind> NODE_KINDS = new HashMap<>();

    static {
        for (Kind kind : Kind.values()) {
            IElementType type = switch (kind) {
                case WHITESPACE -> TokenType.WHITE_SPACE;
                case BAD_CHARACTER -> TokenType.BAD_CHARACTER;
                default -> new IElementType(kind.name(), NixLanguage.INSTANCE);
            };
            TOKENS.put(kind, type);
            KINDS.put(type, kind);
        }
        for (NodeKind kind : NodeKind.values()) {
            IElementType type = new IElementType(kind.name(), NixLanguage.INSTANCE);
            NODES.put(kind, type);
            NODE_KINDS.put(type, kind);
        }
    }

    public static final TokenSet COMMENTS = TokenSet.create(of(Kind.COMMENT));
    public static final TokenSet STRING_CONTENT = TokenSet.create(of(Kind.STR_TEXT), of(Kind.STR_ESCAPE));
    public static final TokenSet STRINGS = TokenSet.create(of(NodeKind.STRING), of(NodeKind.IND_STRING));
    /** What a variable can resolve to. */
    public static final TokenSet NAMES = TokenSet.create(of(NodeKind.ATTR_NAME), of(NodeKind.PARAM), of(NodeKind.FORMAL));

    private NixElementTypes() {
    }

    public static IElementType of(Kind kind) {
        return TOKENS.get(kind);
    }

    public static IElementType of(NodeKind kind) {
        return NODES.get(kind);
    }

    /** The node kind of an element type, or null for a token or another language's. */
    static NodeKind nodeKind(IElementType type) {
        return NODE_KINDS.get(type);
    }

    /** The lexeme kind of a token type, or null for another language's. */
    static Kind kind(IElementType type) {
        return KINDS.get(type);
    }
}
