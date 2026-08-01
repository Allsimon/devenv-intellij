package com.allsimon.intellij.jdk;

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

/**
 * The JDK a devenv project declares, as {@code devenv eval} reports it.
 * <p>
 * Two attributes are read in a single evaluation: whether {@code languages.java} is enabled at all,
 * and where its JDK lives.
 * <p>
 * The path has to come from {@code languages.java.jdk.package.home} rather than from the package
 * itself: {@code languages.java.jdk.package} evaluates to the store path of the derivation, and for
 * the nixpkgs OpenJDK that directory only holds {@code bin}, {@code include}, {@code lib} and
 * {@code share} - no {@code release}, no {@code conf}, no {@code jmods} - so the IDE rejects it as a
 * JDK home. The {@code home} attribute points at {@code <store path>/lib/openjdk}, which is the real
 * JDK home and exactly what the devenv shell exports as {@code JAVA_HOME}.
 */
final class DevenvJdk {
    private static final Logger LOG = Logger.getInstance(DevenvJdk.class);

    static final String ENABLE_ATTRIBUTE = "languages.java.enable";
    /** Deliberately '.home' and not '.package'; see the class documentation. */
    static final String HOME_ATTRIBUTE = "languages.java.jdk.package.home";

    private DevenvJdk() {
    }

    /**
     * The JDK home of {@code devenvRoot}, or {@code null} when the project does not enable
     * {@code languages.java} or devenv could not be asked.
     * <p>
     * The path is what devenv <em>would</em> build; the store path may well not exist yet, so callers
     * have to check before handing it to the IDE.
     */
    static @Nullable String evaluateHome(@NotNull File executable, @NotNull VirtualFile devenvRoot) {
        ProcessOutput output;
        try {
            // No timeout: the first evaluation of a project has to evaluate the whole devenv
            // configuration, which takes minutes against a cold nix eval cache.
            output = DevenvCli.run(DevenvCli.commandLine(
                    executable, devenvRoot.getPath(), "eval", ENABLE_ATTRIBUTE, HOME_ATTRIBUTE));
        } catch (ExecutionException e) {
            LOG.warn("Failed to run 'devenv eval' in " + devenvRoot.getPath(), e);
            return null;
        }

        if (output.getExitCode() != 0) {
            LOG.warn("'devenv eval " + ENABLE_ATTRIBUTE + " " + HOME_ATTRIBUTE + "' exited with "
                    + output.getExitCode() + ": " + output.getStderr());
            return null;
        }

        try {
            return parseHome(output.getStdout());
        } catch (RuntimeException e) {
            LOG.warn("Failed to parse 'devenv eval' output", e);
            return null;
        }
    }

    /**
     * Reads the {@code {"languages.java.enable": true, "languages.java.jdk.package.home": "/nix/store/..."}}
     * document {@code devenv eval} prints, and returns the home only when Java is actually enabled -
     * the JDK attribute has a default and evaluates just as well in a project that never asked for
     * Java.
     */
    static @Nullable String parseHome(@NotNull String evalOutput) {
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

        JsonElement home = object.get(HOME_ATTRIBUTE);
        if (home == null || !home.isJsonPrimitive() || !home.getAsJsonPrimitive().isString()) {
            return null;
        }
        String path = home.getAsString().strip();
        return path.isEmpty() ? null : path;
    }
}
