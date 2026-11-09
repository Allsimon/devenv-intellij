package com.allsimon.intellij.nixlang;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/** Its id is not "Nix", which NixIDEA's language takes. */
public final class NixLanguage extends Language {
    public static final NixLanguage INSTANCE = new NixLanguage();

    private NixLanguage() {
        super("AffogatoNix");
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Nix";
    }
}
