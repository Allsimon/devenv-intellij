package com.allsimon.intellij.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Keeps devenv's '.devenv' state directory out of indexing, search and refactoring - the equivalent
 * of marking it 'Excluded' by hand.
 * <p>
 * It holds nothing a developer edits: generated per-session shell scripts, an eval cache, and
 * symlinks pointing into the Nix store and into the runtime directory. Indexing those means indexing
 * whatever they resolve to, which is how a whole Nix profile ends up in the project's index.
 * <p>
 * Excluding this way only affects this IDE's view of the project: unlike 'Mark Directory as |
 * Excluded' it doesn't touch the module's .iml file, so nothing lands in version control. The
 * flip side is that the exclusion can't be undone from the project tree.
 */
public final class DevenvExcludePolicy implements DirectoryIndexExcludePolicy {
    private final Project project;

    public DevenvExcludePolicy(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public String @NotNull [] getExcludeUrlsForProject() {
        // Every root, not just the outermost one: each devenv.nix gets a state directory of its own, and
        // a module's is as full of store symlinks as the project's.
        List<VirtualFile> devenvRoots = DevenvCli.findDevenvRoots(project);
        if (devenvRoots.isEmpty()) {
            return ArrayUtilRt.EMPTY_STRING_ARRAY;
        }
        // The directories need not exist yet - devenv creates one on the first 'devenv shell'.
        return devenvRoots.stream()
                .map(root -> root.getUrl() + "/" + DevenvCli.STATE_DIRECTORY)
                .toArray(String[]::new);
    }
}
