package com.allsimon.intellij.javascript;

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
 * The JavaScript toolchain a devenv project declares, as {@code devenv eval} reports it: the Node.js
 * to run on, and the package manager to run with it.
 * <p>
 * Unlike the Gradle and Maven homes, what the IDE wants for the runtime is not a directory but the
 * interpreter binary itself, and the nixpkgs Node.js puts it exactly where a Node.js distribution
 * always does - {@code <store path>/bin/node}, the very binary the devenv shell puts first on
 * {@code PATH}. There is no candidate list to try, only that one path, and it is checked before being
 * handed over: a store path that has been evaluated but never built holds nothing at all. Where the
 * package managers live is {@link DevenvPackageManager}'s business.
 * <p>
 * The two are evaluated separately rather than in one call. The second evaluation is cheap - it is
 * the first that pays for the whole devenv configuration, and it warms the nix eval cache for
 * everything after it - and keeping them apart means a devenv that has never heard of one of the
 * package manager options still gets its interpreter configured.
 */
final class DevenvJavascript {
    private static final Logger LOG = Logger.getInstance(DevenvJavascript.class);

    static final String ENABLE_ATTRIBUTE = "languages.javascript.enable";
    static final String PACKAGE_ATTRIBUTE = "languages.javascript.package";

    /** Relative to the store path. What the IDE runs, and what it reads the Node.js version from. */
    private static final String INTERPRETER = "bin/node";

    private DevenvJavascript() {
    }

    /** A package manager the project enables, and the store path devenv declares for it. */
    record Declared(@NotNull DevenvPackageManager manager, @NotNull String storePath) {
    }

    /**
     * The Node.js interpreter of {@code devenvRoot}, or {@code null} when the project does not enable
     * {@code languages.javascript}, devenv could not be asked, or nothing runnable is there - the last
     * of which is the normal state of a store path that has been evaluated but never built.
     */
    static @Nullable Path resolveInterpreter(@NotNull File executable, @NotNull VirtualFile devenvRoot) {
        String output = eval(executable, devenvRoot, ENABLE_ATTRIBUTE, PACKAGE_ATTRIBUTE);
        if (output == null) {
            return null;
        }

        String storePath;
        try {
            storePath = parsePackage(output);
        } catch (RuntimeException e) {
            LOG.warn("Failed to parse 'devenv eval' output", e);
            return null;
        }
        if (storePath == null) {
            return null;
        }

        Path interpreter = findInterpreter(Path.of(storePath));
        if (interpreter == null) {
            LOG.warn("devenv declares a Node.js at " + storePath + ", which holds no " + INTERPRETER);
        }
        return interpreter;
    }

    /**
     * What the IDE is to run as the package manager of {@code devenvRoot}, or {@code null} when the
     * project enables none of them, devenv could not be asked, or the store path of the one it enables
     * has never been built.
     */
    static @Nullable Path resolvePackageManager(@NotNull File executable, @NotNull VirtualFile devenvRoot) {
        List<String> attributes = new ArrayList<>();
        for (DevenvPackageManager manager : DevenvPackageManager.values()) {
            attributes.add(manager.enableAttribute());
            attributes.add(manager.packageAttribute());
        }

        String output = eval(executable, devenvRoot, attributes.toArray(String[]::new));
        if (output == null) {
            return null;
        }

        Declared declared;
        try {
            declared = parsePackageManager(output);
        } catch (RuntimeException e) {
            LOG.warn("Failed to parse 'devenv eval' output", e);
            return null;
        }
        if (declared == null) {
            return null;
        }

        Path packageDirectory = declared.manager().findPackage(Path.of(declared.storePath()));
        if (packageDirectory == null) {
            LOG.warn("devenv declares a " + declared.manager().packageName() + " at " + declared.storePath()
                    + ", which holds no package the IDE can run");
        }
        return packageDirectory;
    }

    /** The stdout of {@code devenv eval <attributes>}, or {@code null} when it could not be run. */
    private static @Nullable String eval(@NotNull File executable, @NotNull VirtualFile devenvRoot,
                                         String @NotNull ... attributes) {
        String[] arguments = new String[attributes.length + 1];
        arguments[0] = "eval";
        System.arraycopy(attributes, 0, arguments, 1, attributes.length);

        ProcessOutput output;
        try {
            // No timeout: the first evaluation of a project has to evaluate the whole devenv
            // configuration, which takes minutes against a cold nix eval cache.
            output = DevenvCli.run(DevenvCli.commandLine(executable, devenvRoot.getPath(), arguments));
        } catch (ExecutionException e) {
            LOG.warn("Failed to run 'devenv eval' in " + devenvRoot.getPath(), e);
            return null;
        }

        if (output.getExitCode() != 0) {
            LOG.warn("'devenv eval " + String.join(" ", attributes) + "' exited with " + output.getExitCode()
                    + ": " + output.getStderr());
            return null;
        }
        return output.getStdout();
    }

    /**
     * Reads the {@code {"languages.javascript.enable": true, "languages.javascript.package": "/nix/store/..."}}
     * document {@code devenv eval} prints, and returns the store path only when JavaScript is actually
     * enabled - the package attribute has a default and evaluates just as well in a project that never
     * asked for Node.js.
     */
    static @Nullable String parsePackage(@NotNull String evalOutput) {
        JsonObject object = parseObject(evalOutput);
        if (object == null || !isEnabled(object, ENABLE_ATTRIBUTE)) {
            return null;
        }
        return readStorePath(object, PACKAGE_ATTRIBUTE);
    }

    /**
     * Reads the same kind of document for all of the package manager attributes at once, and returns
     * the first manager the project enables, in {@link DevenvPackageManager}'s own order. Every
     * package attribute in there evaluates whether or not its manager is enabled, which is what makes
     * the flags necessary.
     */
    static @Nullable Declared parsePackageManager(@NotNull String evalOutput) {
        JsonObject object = parseObject(evalOutput);
        if (object == null) {
            return null;
        }
        for (DevenvPackageManager manager : DevenvPackageManager.values()) {
            if (!isEnabled(object, manager.enableAttribute())) {
                continue;
            }
            String storePath = readStorePath(object, manager.packageAttribute());
            if (storePath != null) {
                return new Declared(manager, storePath);
            }
        }
        return null;
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

    private static boolean isEnabled(@NotNull JsonObject object, @NotNull String attribute) {
        JsonElement enabled = object.get(attribute);
        return enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()
                && enabled.getAsBoolean();
    }

    private static @Nullable String readStorePath(@NotNull JsonObject object, @NotNull String attribute) {
        JsonElement storePath = object.get(attribute);
        if (storePath == null || !storePath.isJsonPrimitive() || !storePath.getAsJsonPrimitive().isString()) {
            return null;
        }
        String path = storePath.getAsString().strip();
        return path.isEmpty() ? null : path;
    }

    /**
     * The {@code bin/node} of {@code storePath}, when there is one to run - which is the whole of what
     * the IDE asks of a local Node.js interpreter.
     */
    static @Nullable Path findInterpreter(@NotNull Path storePath) {
        Path interpreter = storePath.resolve(INTERPRETER);
        return Files.isRegularFile(interpreter) && Files.isExecutable(interpreter) ? interpreter : null;
    }
}
