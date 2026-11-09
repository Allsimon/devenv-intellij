package com.allsimon.intellij.nixlang;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class NixFile extends PsiFileBase {
    NixFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, NixLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return NixFileType.INSTANCE;
    }
}
