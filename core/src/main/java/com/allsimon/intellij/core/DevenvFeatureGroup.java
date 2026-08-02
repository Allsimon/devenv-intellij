package com.allsimon.intellij.core;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * How the features are grouped on the settings page, following devenv's own vocabulary rather than
 * this plugin's module layout: a reader of devenv.nix should recognise every heading.
 * <p>
 * Declaration order is the order of the sections on the page.
 */
public enum DevenvFeatureGroup {
    /** The server 'devenv lsp' starts - a devenv command rather than a devenv.nix option. */
    LSP("lsp"),
    /** Everything under 'languages' in devenv.nix. */
    LANGUAGES("languages"),
    /** Everything under 'processes' in devenv.nix. */
    PROCESSES("processes"),
    /** The formatter devenv runs, which is treefmt. */
    FORMATTER("formatter"),
    /** What devenv leaves in the project directory rather than anything it declares. */
    PROJECT("project");

    private final String key;

    DevenvFeatureGroup(@NotNull String key) {
        this.key = key;
    }

    public @Nls @NotNull String displayName() {
        return MyMessageBundle.message("settings.devenv.group." + key);
    }
}
