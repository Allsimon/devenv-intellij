package com.allsimon.intellij.nixlang;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class NixFileType extends LanguageFileType {
    public static final NixFileType INSTANCE = new NixFileType();

    private NixFileType() {
        super(NixLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "AffogatoNix";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Nix";
    }

    @Override
    public @NotNull String getDescription() {
        return "Nix";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "nix";
    }

    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Config;
    }
}
