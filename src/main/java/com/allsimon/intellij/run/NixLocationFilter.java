package com.allsimon.intellij.run;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links the {@code file.nix:line:column} positions Nix prints. A {@code /nix/store/<hash>-source/} path is the copy
 * of a flake Nix evaluated, so it leads to the same file in the project when there is one.
 */
public final class NixLocationFilter implements Filter {
    private static final Pattern LOCATION = Pattern.compile(
            "(/nix/store/[0-9a-z]{32}-source/([^\\s:'\"]+\\.nix)|/[^\\s:'\"]+\\.nix):(\\d+)(?::(\\d+))?");

    private final Project project;

    NixLocationFilter(Project project) {
        this.project = project;
    }

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
        int lineStart = entireLength - line.length();
        List<ResultItem> items = new ArrayList<>();
        Matcher matcher = LOCATION.matcher(line);
        while (matcher.find()) {
            VirtualFile file = file(matcher.group(1), matcher.group(2));
            if (file == null) {
                continue;
            }
            int lineNumber = Math.max(0, Integer.parseInt(matcher.group(3)) - 1);
            int column = matcher.group(4) == null ? 0 : Math.max(0, Integer.parseInt(matcher.group(4)) - 1);
            items.add(new ResultItem(lineStart + matcher.start(), lineStart + matcher.end(),
                    new OpenFileHyperlinkInfo(project, file, lineNumber, column)));
        }
        return items.isEmpty() ? null : new Result(items);
    }

    /** The project's copy of a flake source file, else the file at the path itself. */
    private @Nullable VirtualFile file(String path, @Nullable String inSource) {
        if (inSource != null) {
            for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
                VirtualFile copy = root.findFileByRelativePath(inSource);
                if (copy != null) {
                    return copy;
                }
            }
        }
        return LocalFileSystem.getInstance().findFileByPath(path);
    }

    public static final class Provider implements ConsoleFilterProvider {
        @Override
        public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
            return new Filter[]{new NixLocationFilter(project)};
        }
    }
}
