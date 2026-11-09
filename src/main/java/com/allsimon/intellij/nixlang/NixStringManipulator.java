package com.allsimon.intellij.nixlang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import org.jetbrains.annotations.NotNull;

public final class NixStringManipulator extends AbstractElementManipulator<NixStringElement> {
    @Override
    public NixStringElement handleContentChange(@NotNull NixStringElement element, @NotNull TextRange range,
                                                String newContent) {
        return (NixStringElement) element.updateText(range.replace(element.getText(), newContent));
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull NixStringElement element) {
        return element.contentRange();
    }
}
