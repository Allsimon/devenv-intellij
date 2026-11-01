package com.allsimon.intellij.maven;

import com.allsimon.intellij.core.DevenvCli;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Maven a devenv project declares, as {@code devenv eval} reports it.
 * <p>
 * Whether {@code languages.java} and {@code languages.java.maven} are enabled is read first, and where
 * its Maven lives only when both are: the package option has no value otherwise, and evaluating it fails.
 * <p>
 * As with the Gradle it declares, the store path is not itself the home the IDE wants: the nixpkgs
 * Maven copies the distribution into {@code <store path>/maven} and leaves a wrapper script in
 * {@code bin}, while a Maven home is the directory holding {@code bin/m2.conf}. The candidates below
 * are tried in turn rather than hard-coded, so a distribution laid out flat, as the ones unpacked
 * from apache.org are, is found just as well.
 */
final class DevenvMaven {
    private static final Logger LOG = Logger.getInstance(DevenvMaven.class);

    static final String JAVA_ENABLE_ATTRIBUTE = "languages.java.enable";
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
        String flags = eval(executable, devenvRoot, JAVA_ENABLE_ATTRIBUTE, ENABLE_ATTRIBUTE);
        if (flags == null || !parseEnabled(flags)) {
            return null;
        }
        String storePath = eval(executable, devenvRoot, PACKAGE_ATTRIBUTE);
        return storePath == null ? null : parsePackage(storePath);
    }

    private static @Nullable String eval(@NotNull File executable, @NotNull VirtualFile devenvRoot,
                                         @NotNull String... attributes) {
        ProcessOutput output;
        try {
            // No timeout: the first evaluation of a project has to evaluate the whole devenv
            // configuration, which takes minutes against a cold nix eval cache.
            List<String> arguments = new ArrayList<>(List.of("eval"));
            arguments.addAll(List.of(attributes));
            output = DevenvCli.run(DevenvCli.commandLine(
                    executable, devenvRoot.getPath(), arguments.toArray(String[]::new)));
        } catch (ExecutionException e) {
            LOG.warn("Failed to run 'devenv eval' in " + devenvRoot.getPath(), e);
            return null;
        }

        if (output.getExitCode() != 0) {
            LOG.warn("'devenv eval " + String.join(" ", attributes) + "' exited with "
                    + output.getExitCode() + ": " + output.getStderr());
            return null;
        }
        return output.getStdout();
    }

    /**
     * Reads the {@code {"languages.java.enable": true, "languages.java.maven.enable": true}} document
     * {@code devenv eval} prints: whether the project builds with the Maven of {@code languages.java}.
     */
    static boolean parseEnabled(@NotNull String evalOutput) {
        JsonObject object = parseObject(evalOutput);
        return object != null && isTrue(object.get(JAVA_ENABLE_ATTRIBUTE)) && isTrue(object.get(ENABLE_ATTRIBUTE));
    }

    private static boolean isTrue(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    /** Reads the {@code {"languages.java.maven.package": "/nix/store/..."}} document {@code devenv eval} prints. */
    static @Nullable String parsePackage(@NotNull String evalOutput) {
        JsonObject object = parseObject(evalOutput);
        if (object == null) {
            return null;
        }
        JsonElement storePath = object.get(PACKAGE_ATTRIBUTE);
        if (storePath == null || !storePath.isJsonPrimitive() || !storePath.getAsJsonPrimitive().isString()) {
            return null;
        }
        String path = storePath.getAsString().strip();
        return path.isEmpty() ? null : path;
    }

    private static @Nullable JsonObject parseObject(@NotNull String evalOutput) {
        JsonElement root;
        try {
            root = JsonParser.parseString(DevenvCli.stripAnsi(evalOutput));
        } catch (JsonSyntaxException e) {
            // Anything but JSON means devenv printed something else entirely - a progress line or a
            // message this version doesn't put on stderr. Nothing to read a store path out of.
            LOG.warn("'devenv eval' printed no JSON document", e);
            return null;
        }
        return root.isJsonObject() ? root.getAsJsonObject() : null;
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
