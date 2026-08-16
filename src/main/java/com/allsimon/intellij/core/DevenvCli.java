package com.allsimon.intellij.core;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Locating the devenv CLI and invoking it the way this plugin needs it invoked.
 * <p>
 * This is the whole of what the feature modules share: the language server support, the Services
 * tool window and the treefmt formatter all reach devenv through here and never through each other.
 */
public final class DevenvCli {
    public static final String EXECUTABLE = "devenv";

    private static final String CONFIG_FILE = "devenv.nix";
    private static final String LOCK_FILE = "devenv.lock";
    /** Where devenv keeps generated shell scripts, caches and symlinks into the Nix store. */
    public static final String STATE_DIRECTORY = ".devenv";

    // devenv colours its output even when stdout/stderr are pipes, and honours neither NO_COLOR nor
    // CLICOLOR=0, so every byte we parse has to be stripped first.
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\e\\[[;?0-9]*[ -/]*[@-~]");

    private DevenvCli() {
    }

    /**
     * The base project directory holding a 'devenv.nix', or {@code null} when this is not a devenv project.
     */
    public static @Nullable VirtualFile findDevenvRoot(@NotNull Project project) {
        for (VirtualFile baseDirectory : BaseProjectDirectories.getBaseDirectories(project)) {
            if (baseDirectory.findChild(CONFIG_FILE) != null) {
                return baseDirectory;
            }
        }
        return null;
    }

    /** Whether {@code file} is one of the files that invalidates everything we cached about a project. */
    public static boolean isConfigurationFile(@NotNull VirtualFile file) {
        String name = file.getName();
        return CONFIG_FILE.equals(name) || LOCK_FILE.equals(name);
    }

    public static @Nullable File findExecutable() {
        return PathEnvironmentVariableUtil.findInPath(EXECUTABLE);
    }

    /**
     * A command line for {@code devenv <arguments>} rooted at {@code workDirectory}.
     * <p>
     * devenv derives the environment it describes from the working directory, so it has to run where
     * devenv.nix lives. CONSOLE parent environment gives it the same PATH and Nix variables a terminal
     * would, which matters when the IDE is started from a desktop launcher.
     */
    public static @NotNull GeneralCommandLine commandLine(@NotNull File executable, @NotNull String workDirectory,
                                                   String @NotNull ... arguments) {
        return new GeneralCommandLine(executable.getAbsolutePath())
                .withParameters(arguments)
                .withWorkDirectory(workDirectory)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withCharset(StandardCharsets.UTF_8);
    }

    public static @NotNull ProcessOutput run(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
        return ExecUtil.execAndGetOutput(commandLine);
    }

    /** As {@link #run(GeneralCommandLine)}, but kills the process once {@code timeoutMillis} elapses. */
    public static @NotNull ProcessOutput run(@NotNull GeneralCommandLine commandLine, int timeoutMillis)
            throws ExecutionException {
        return ExecUtil.execAndGetOutput(commandLine, timeoutMillis);
    }

    /** Removes the CSI escape sequences devenv writes even when it isn't attached to a terminal. */
    public static @NotNull String stripAnsi(@NotNull String text) {
        return ANSI_ESCAPE.matcher(text).replaceAll("");
    }
}
