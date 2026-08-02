package com.allsimon.intellij.core;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The features of this plugin the user can turn off, one per feature module.
 * <p>
 * Every one of them is on by default: the plugin's job is to make a devenv project work out of the
 * box, and each feature only ever does something in a project that declares what it needs. The
 * switches are there for the cases where a devenv project is deliberately not the whole truth - a
 * Project SDK or a Gradle distribution chosen by hand, which the plugin would otherwise keep putting
 * back.
 * <p>
 * The constant names are persisted in {@link DevenvSettings}, so renaming one silently resets that
 * feature to enabled for everyone who had turned it off. Their order, on the other hand, is only the
 * order of the settings page, grouped the way devenv itself talks about these things.
 */
public enum DevenvFeature {
    LSP("lsp", DevenvFeatureGroup.LSP, null),
    JDK("jdk", DevenvFeatureGroup.LANGUAGES, "com.intellij.java"),
    GRADLE("gradle", DevenvFeatureGroup.LANGUAGES, "com.intellij.gradle"),
    MAVEN("maven", DevenvFeatureGroup.LANGUAGES, "org.jetbrains.idea.maven"),
    PROCESSES("processes", DevenvFeatureGroup.PROCESSES, null),
    TREEFMT("treefmt", DevenvFeatureGroup.FORMATTER, null),
    EXCLUDE("exclude", DevenvFeatureGroup.PROJECT, null);

    private final String key;
    private final DevenvFeatureGroup group;
    private final @Nullable String requiredPluginId;

    DevenvFeature(@NotNull String key, @NotNull DevenvFeatureGroup group, @Nullable String requiredPluginId) {
        this.key = key;
        this.group = group;
        this.requiredPluginId = requiredPluginId;
    }

    public @NotNull DevenvFeatureGroup group() {
        return group;
    }

    public @Nls @NotNull String displayName() {
        return MyMessageBundle.message("settings.devenv.feature." + key + ".name");
    }

    /**
     * The comment shown under the switch. Rendered as HTML, so {@code <br/>} in the bundle breaks a
     * line where the text stops describing the feature and starts saying where to set the thing by
     * hand instead; everything else wraps on its own.
     */
    public @Nls @NotNull String description() {
        return MyMessageBundle.message("settings.devenv.feature." + key + ".description");
    }

    /**
     * Whether this IDE has the plugin the feature is built on, so that the settings page doesn't
     * offer a switch that could change nothing - the Project SDK, Gradle and Maven features live in
     * modules loaded only alongside their plugin, exactly as the optional {@code <depends>} in
     * plugin.xml describes.
     */
    public boolean isAvailable() {
        if (requiredPluginId == null) {
            return true;
        }
        return PluginManagerCore.isLoaded(PluginId.getId(requiredPluginId));
    }
}
