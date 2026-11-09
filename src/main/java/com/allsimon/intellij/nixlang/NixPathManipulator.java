package com.allsimon.intellij.nixlang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/** Rewrites a path when the file it names moves or is renamed. */
public final class NixPathManipulator extends AbstractElementManipulator<NixPathElement> {
    @Override
    public NixPathElement handleContentChange(@NotNull NixPathElement element, @NotNull TextRange range,
                                              String newContent) {
        String text = range.replace(element.getText(), newContent);
        if (!text.startsWith(".") && !text.startsWith("/") && !text.startsWith("~")) {
            text = "./" + text;
        }
        var file = PsiFileFactory.getInstance(element.getProject())
                .createFileFromText("dummy.nix", NixFileType.INSTANCE, text);
        NixPathElement path = PsiTreeUtil.findChildOfType(file, NixPathElement.class);
        if (path == null || !path.getText().equals(text)) {
            throw new IncorrectOperationException("'" + text + "' is not a Nix path");
        }
        return (NixPathElement) element.replace(path);
    }
}
