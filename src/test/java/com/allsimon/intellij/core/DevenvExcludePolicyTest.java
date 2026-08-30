package com.allsimon.intellij.core;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * The state directory is excluded per root: every devenv.nix of a project has a '.devenv' of its own,
 * and a module's is as full of Nix store symlinks as the project's.
 */
public class DevenvExcludePolicyTest extends DevenvProjectTestCase {

    public void testExcludesTheStateDirectoryOfEveryRoot() {
        VirtualFile first = moduleWithDevenvNix("first");
        VirtualFile second = moduleWithDevenvNix("second");

        assertOrderedEquals(new DevenvExcludePolicy(getProject()).getExcludeUrlsForProject(),
                first.getUrl() + "/.devenv", second.getUrl() + "/.devenv");
    }

    public void testExcludesNothingInAProjectWithoutADevenvNix() {
        assertEmpty(new DevenvExcludePolicy(getProject()).getExcludeUrlsForProject());
    }
}
