package com.allsimon.intellij.javascript;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The package managers devenv can provide under {@code languages.javascript}, and what each one looks
 * like once it is in the Nix store.
 * <p>
 * They are declared in the order they are tried, and the first one the project enables wins - the IDE
 * has a single Package manager setting, while devenv is perfectly happy to provide several. The order
 * runs from the most deliberate choice to the least: pnpm and yarn are picked over the default on
 * purpose, and npm is what a project gets for merely having Node.js.
 * <p>
 * What the IDE wants is the package's own directory - the one holding its {@code package.json}, which
 * is where it looks for the CLI entry point to run - and not the store path, which holds a wrapper
 * script in {@code bin} and the package one or two levels down: under {@code lib/node_modules} for
 * npm, under {@code libexec} for pnpm and yarn. Both are tried, and the directory has to be named
 * after the manager either way: the IDE reads the name off the path and decides from it whether it is
 * driving npm, yarn or pnpm.
 */
enum DevenvPackageManager {
    PNPM("pnpm"),
    YARN("yarn"),
    NPM("npm");

    /** Where the package directory sits relative to the store path, in the order they are tried. */
    private static final List<String> PACKAGE_PARENTS = List.of("lib/node_modules", "libexec");

    /** What the IDE identifies a package by, and reads its CLI entry point from. */
    private static final String MARKER = "package.json";

    private final String packageName;

    DevenvPackageManager(@NotNull String packageName) {
        this.packageName = packageName;
    }

    /** Also the name the IDE reads back off the path it is given, so it has to end up in that path. */
    @NotNull String packageName() {
        return packageName;
    }

    @NotNull String enableAttribute() {
        return "languages.javascript." + packageName + ".enable";
    }

    @NotNull String packageAttribute() {
        return "languages.javascript." + packageName + ".package";
    }

    /**
     * What the IDE is to run this manager from, inside {@code storePath}, or {@code null} when nothing
     * is there - the normal state of a store path that has been evaluated but never built.
     */
    @Nullable Path findPackage(@NotNull Path storePath) {
        for (String parent : PACKAGE_PARENTS) {
            Path directory = storePath.resolve(parent).resolve(packageName);
            if (Files.isRegularFile(directory.resolve(MARKER))) {
                return directory;
            }
        }
        return null;
    }
}
