package com.allsimon.intellij;

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
 * Locating the devenv CLI and invoking it the way this plugin needs it invoked. Shared by the
 * language server support ({@link DevenvLspServerDescriptor}) and the Services tool window
 * ({@link DevenvProcessManager}).
 */
final class DevenvCli {
    static final String EXECUTABLE = "devenv";

    private static final String CONFIG_FILE = "devenv.nix";
    private static final String LOCK_FILE = "devenv.lock";
    /** Where devenv keeps generated shell scripts, caches and symlinks into the Nix store. */
    static final String STATE_DIRECTORY = ".devenv";

    // devenv colours its output even when stdout/stderr are pipes, and honours neither NO_COLOR nor
    // CLICOLOR=0, so every byte we parse has to be stripped first.
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\e\\[[;?0-9]*[ -/]*[@-~]");

    private DevenvCli() {
    }

    /**
     * The base project directory holding a 'devenv.nix', or {@code null} when this is not a devenv project.
     */
    static @Nullable VirtualFile findDevenvRoot(@NotNull Project project) {
        for (VirtualFile baseDirectory : BaseProjectDirectories.getBaseDirectories(project)) {
            if (baseDirectory.findChild(CONFIG_FILE) != null) {
                return baseDirectory;
            }
        }
        return null;
    }

    /** Whether {@code file} is one of the files that invalidates everything we cached about a project. */
    static boolean isConfigurationFile(@NotNull VirtualFile file) {
        String name = file.getName();
        return CONFIG_FILE.equals(name) || LOCK_FILE.equals(name);
    }

    static @Nullable File findExecutable() {
        return PathEnvironmentVariableUtil.findInPath(EXECUTABLE);
    }

    /**
     * A command line for {@code devenv <arguments>} rooted at {@code workDirectory}.
     * <p>
     * devenv derives the environment it describes from the working directory, so it has to run where
     * devenv.nix lives. CONSOLE parent environment gives it the same PATH and Nix variables a terminal
     * would, which matters when the IDE is started from a desktop launcher.
     */
    static @NotNull GeneralCommandLine commandLine(@NotNull File executable, @NotNull String workDirectory,
                                                   String @NotNull ... arguments) {
        return new GeneralCommandLine(executable.getAbsolutePath())
                .withParameters(arguments)
                .withWorkDirectory(workDirectory)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withCharset(StandardCharsets.UTF_8);
    }

    static @NotNull ProcessOutput run(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
        return ExecUtil.execAndGetOutput(commandLine);
    }

    /** As {@link #run(GeneralCommandLine)}, but kills the process once {@code timeoutMillis} elapses. */
    static @NotNull ProcessOutput run(@NotNull GeneralCommandLine commandLine, int timeoutMillis)
            throws ExecutionException {
        return ExecUtil.execAndGetOutput(commandLine, timeoutMillis);
    }

    /** Removes the CSI escape sequences devenv writes even when it isn't attached to a terminal. */
    static @NotNull String stripAnsi(@NotNull String text) {
        return ANSI_ESCAPE.matcher(text).replaceAll("");
    }
}
