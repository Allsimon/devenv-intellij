package com.allsimon.intellij.core;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
     * A project directory holding a 'devenv.nix', or {@code null} when this is not a devenv project.
     * <p>
     * The outermost one of {@link #findDevenvRoots(Project)}, for the features that configure the
     * project as a whole and so have a single environment to read.
     */
    public static @Nullable VirtualFile findDevenvRoot(@NotNull Project project) {
        for (VirtualFile baseDirectory : BaseProjectDirectories.getBaseDirectories(project)) {
            if (baseDirectory.findChild(CONFIG_FILE) != null) {
                return baseDirectory;
            }
        }
        // Only a project whose base directories carry no devenv.nix pays for the wider search below.
        List<VirtualFile> roots = findDevenvRoots(project);
        return roots.isEmpty() ? null : roots.get(0);
    }

    /**
     * The devenv root {@code file} belongs to - the innermost one holding it - or {@code null} when it
     * lies outside all of them.
     * <p>
     * What the per-file features go through: a file is described by the environment declared closest
     * to it, so a module carrying its own devenv.nix is read with its own devenv rather than the one
     * of the repository it sits in.
     */
    public static @Nullable VirtualFile findDevenvRootFor(@NotNull Project project, @NotNull VirtualFile file) {
        VirtualFile nearest = null;
        // Outermost first, and a root always sorts before the roots nested under it, so the last root
        // holding the file is the innermost one.
        for (VirtualFile root : findDevenvRoots(project)) {
            if (VfsUtilCore.isAncestor(root, file, false)) {
                nearest = root;
            }
        }
        return nearest;
    }

    /**
     * Every directory of the project holding a 'devenv.nix', outermost first.
     * <p>
     * A project can hold several: modules attached side by side, each with an environment of its own,
     * or a repository whose modules live under one project directory. The base directories only ever
     * name the outermost of those - the platform collapses nested content roots out of that set - so
     * the content roots of the modules are searched as well.
     */
    public static @NotNull List<VirtualFile> findDevenvRoots(@NotNull Project project) {
        if (project.isDisposed()) {
            return List.of();
        }

        Set<VirtualFile> candidates = new LinkedHashSet<>(BaseProjectDirectories.getBaseDirectories(project));
        // Reading the module roots needs read access, and this runs on the process poller as much as on
        // the UI path.
        Collections.addAll(candidates,
                ReadAction.compute(() -> ProjectRootManager.getInstance(project).getContentRoots()));

        List<VirtualFile> roots = new ArrayList<>();
        for (VirtualFile candidate : candidates) {
            // A root that was just deleted can still be listed: this runs from inside the write action
            // that deletes it, among others.
            if (candidate.isValid() && candidate.findChild(CONFIG_FILE) != null) {
                roots.add(candidate);
            }
        }
        // By path, so that a root is listed before the roots nested under it and the order a project
        // shows its environments in doesn't depend on how the module roots happen to be enumerated.
        roots.sort(Comparator.comparing(VirtualFile::getPath));
        return List.copyOf(roots);
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
