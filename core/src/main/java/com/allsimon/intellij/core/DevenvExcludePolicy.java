package com.allsimon.intellij.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

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
        VirtualFile devenvRoot = DevenvCli.findDevenvRoot(project);
        if (devenvRoot == null) {
            return ArrayUtilRt.EMPTY_STRING_ARRAY;
        }
        // The directory need not exist yet - devenv creates it on the first 'devenv shell'.
        return new String[]{devenvRoot.getUrl() + "/" + DevenvCli.STATE_DIRECTORY};
    }
}
