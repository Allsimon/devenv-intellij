package com.allsimon.intellij.nixhighlight;

import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.tree.IElementType;
import org.graalnix.parser.Highlighter;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

final class NixTokenTypes {
    static final IElementType PLAIN = new IElementType("NIX_PLAIN", PlainTextLanguage.INSTANCE);

    private static final Map<Highlighter.Kind, IElementType> TYPES = new EnumMap<>(Highlighter.Kind.class);

    static {
        for (Highlighter.Kind kind : Highlighter.Kind.values()) {
            TYPES.put(kind, new IElementType("NIX_" + kind.name(), PlainTextLanguage.INSTANCE));
        }
    }

    private NixTokenTypes() {
    }

    static @NotNull IElementType of(@NotNull Highlighter.Kind kind) {
        return TYPES.get(kind);
    }
}
