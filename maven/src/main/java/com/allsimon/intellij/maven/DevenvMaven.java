package com.allsimon.intellij.maven;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Maven a devenv project declares, as {@code devenv eval} reports it.
 * <p>
 * Two attributes are read in a single evaluation: whether {@code languages.java.maven} is enabled at
 * all, and where its Maven lives.
 * <p>
 * As with the Gradle it declares, the store path is not itself the home the IDE wants: the nixpkgs
 * Maven copies the distribution into {@code <store path>/maven} and leaves a wrapper script in
 * {@code bin}, while a Maven home is the directory holding {@code bin/m2.conf}. The candidates below
 * are tried in turn rather than hard-coded, so a distribution laid out flat, as the ones unpacked
 * from apache.org are, is found just as well.
 */
final class DevenvMaven {
    private static final Logger LOG = Logger.getInstance(DevenvMaven.class);

    static final String ENABLE_ATTRIBUTE = "languages.java.maven.enable";
    static final String PACKAGE_ATTRIBUTE = "languages.java.maven.package";

    /** Relative to the store path, in the order they are tried. The empty path is the store path itself. */
    private static final List<String> HOME_CANDIDATES = List.of("maven", "", "libexec/maven", "lib/maven");

    /** What the IDE identifies a Maven home by, and what it reads the distribution's launcher setup from. */
    private static final String MARKER = "bin/m2.conf";

    private DevenvMaven() {
    }

    /**
     * The Maven home of {@code devenvRoot}, or {@code null} when the project does not enable
     * {@code languages.java.maven}, devenv could not be asked, or nothing that looks like a Maven
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
            LOG.warn("devenv declares a Maven at " + storePath + ", which holds no Maven distribution");
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
     * Reads the {@code {"languages.java.maven.enable": true, "languages.java.maven.package": "/nix/store/..."}}
     * document {@code devenv eval} prints, and returns the store path only when Maven is actually
     * enabled - the package attribute has a default and evaluates just as well in a project that
     * builds with something else.
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

    /** The first candidate under {@code storePath} that holds a {@code bin/m2.conf}, or {@code null}. */
    static @Nullable Path findHome(@NotNull Path storePath) {
        for (String candidate : HOME_CANDIDATES) {
            Path home = candidate.isEmpty() ? storePath : storePath.resolve(candidate);
            if (Files.isRegularFile(home.resolve(MARKER))) {
                return home;
            }
        }
        return null;
    }
}
