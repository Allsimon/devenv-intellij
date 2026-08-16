package com.allsimon.intellij.gradle;

import com.allsimon.intellij.core.DevenvCli;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Gradle a devenv project declares, as {@code devenv eval} reports it.
 * <p>
 * Two attributes are read in a single evaluation: whether {@code languages.java.gradle} is enabled at
 * all, and where its Gradle lives.
 * <p>
 * The store path devenv reports is not itself the Gradle home the IDE wants. A Gradle home is the
 * directory holding {@code lib/gradle-launcher-<version>.jar}, and the nixpkgs Gradle keeps the
 * distribution one level down, under {@code libexec/gradle}, with only a wrapper script in
 * {@code bin}. Rather than hard-code that layout, the candidates below are tried in turn and the
 * first one holding a launcher jar wins - so a distribution laid out flat, as the ones downloaded
 * from gradle.org are, is found just as well.
 */
final class DevenvGradle {
    private static final Logger LOG = Logger.getInstance(DevenvGradle.class);

    static final String ENABLE_ATTRIBUTE = "languages.java.gradle.enable";
    static final String PACKAGE_ATTRIBUTE = "languages.java.gradle.package";

    /** Relative to the store path, in the order they are tried. The empty path is the store path itself. */
    private static final List<String> HOME_CANDIDATES = List.of("libexec/gradle", "", "lib/gradle", "share/gradle");

    private static final String LAUNCHER_DIRECTORY = "lib";
    private static final String LAUNCHER_PREFIX = "gradle-launcher-";
    private static final String LAUNCHER_SUFFIX = ".jar";

    private DevenvGradle() {
    }

    /**
     * The Gradle home of {@code devenvRoot}, or {@code null} when the project does not enable
     * {@code languages.java.gradle}, devenv could not be asked, or nothing that looks like a Gradle
     * distribution is there - the last of which is the normal state of a store path that has been
     * evaluated but never built.
     */
    static @Nullable Path resolveHome(@NotNull File executable, @NotNull VirtualFile devenvRoot) {
        String storePath = evaluatePackage(executable, devenvRoot);
        if (storePath == null) {
            return null;
        }

        Path home = findHome(Path.of(storePath));
        if (home == null) {
            LOG.warn("devenv declares a Gradle at " + storePath + ", which holds no Gradle distribution");
        }
        return home;
    }

    private static @Nullable String evaluatePackage(@NotNull File executable, @NotNull VirtualFile devenvRoot) {
        ProcessOutput output;
        try {
            // No timeout: the first evaluation of a project has to evaluate the whole devenv
            // configuration, which takes minutes against a cold nix eval cache.
            output = DevenvCli.run(DevenvCli.commandLine(
                    executable, devenvRoot.getPath(), "eval", ENABLE_ATTRIBUTE, PACKAGE_ATTRIBUTE));
        } catch (ExecutionException e) {
            LOG.warn("Failed to run 'devenv eval' in " + devenvRoot.getPath(), e);
            return null;
        }

        if (output.getExitCode() != 0) {
            LOG.warn("'devenv eval " + ENABLE_ATTRIBUTE + " " + PACKAGE_ATTRIBUTE + "' exited with "
                    + output.getExitCode() + ": " + output.getStderr());
            return null;
        }

        try {
            return parsePackage(output.getStdout());
        } catch (RuntimeException e) {
            LOG.warn("Failed to parse 'devenv eval' output", e);
            return null;
        }
    }

    /**
     * Reads the {@code {"languages.java.gradle.enable": true, "languages.java.gradle.package": "/nix/store/..."}}
     * document {@code devenv eval} prints, and returns the store path only when Gradle is actually
     * enabled - the package attribute has a default and evaluates just as well in a project that
     * builds with something else entirely.
     */
    static @Nullable String parsePackage(@NotNull String evalOutput) {
        JsonElement root = JsonParser.parseString(DevenvCli.stripAnsi(evalOutput));
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();

        JsonElement enabled = object.get(ENABLE_ATTRIBUTE);
        if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()
                || !enabled.getAsBoolean()) {
            return null;
        }

        JsonElement storePath = object.get(PACKAGE_ATTRIBUTE);
        if (storePath == null || !storePath.isJsonPrimitive() || !storePath.getAsJsonPrimitive().isString()) {
            return null;
        }
        String path = storePath.getAsString().strip();
        return path.isEmpty() ? null : path;
    }

    /** The first candidate under {@code storePath} that holds a Gradle launcher jar, or {@code null}. */
    static @Nullable Path findHome(@NotNull Path storePath) {
        for (String candidate : HOME_CANDIDATES) {
            Path home = candidate.isEmpty() ? storePath : storePath.resolve(candidate);
            if (hasLauncher(home)) {
                return home;
            }
        }
        return null;
    }

    /**
     * The one thing the IDE insists on: {@code lib/gradle-launcher-<version>.jar}, which is what it
     * reads the distribution's version from.
     */
    private static boolean hasLauncher(@NotNull Path home) {
        try (Stream<Path> jars = Files.list(home.resolve(LAUNCHER_DIRECTORY))) {
            return jars.map(jar -> jar.getFileName().toString())
                    .anyMatch(name -> name.startsWith(LAUNCHER_PREFIX) && name.endsWith(LAUNCHER_SUFFIX));
        } catch (IOException | RuntimeException e) {
            // No such directory in all but one of the candidates, which is the point of trying them.
            return false;
        }
    }
}
