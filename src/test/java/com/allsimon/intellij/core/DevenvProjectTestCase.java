package com.allsimon.intellij.core;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;

/**
 * A real project with real modules, for the features that can only be pinned down against the module
 * roots of one: which directories count as devenv roots, and what follows from having several.
 */
public abstract class DevenvProjectTestCase extends HeavyPlatformTestCase {

    /** A module of the project, rooted at a directory of its own holding an empty devenv.nix. */
    protected VirtualFile moduleWithDevenvNix(String name) {
        VirtualFile root = createChildDirectory(getOrCreateProjectBaseDir(), name);
        createChildData(root, "devenv.nix");
        PsiTestUtil.addContentRoot(createModule(name), root);
        return root;
    }
}
