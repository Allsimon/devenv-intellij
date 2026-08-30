package com.allsimon.intellij.core;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;

import java.util.List;

/**
 * Pins down which directories of a project count as devenv roots, on a real project with real
 * modules: the base directories of a project only ever name the outermost content roots, so a
 * repository whose modules each carry a devenv.nix needs the module roots to be searched too.
 */
public class DevenvRootsTest extends DevenvProjectTestCase {

    public void testFindsTheDevenvNixOfEveryModule() {
        VirtualFile first = moduleWithDevenvNix("first");
        VirtualFile second = moduleWithDevenvNix("second");

        assertEquals(List.of(first, second), DevenvCli.findDevenvRoots(getProject()));
    }

    /** The single root the whole-project features configure themselves from is the outermost one. */
    public void testTheFirstRootIsTheOutermostOne() {
        VirtualFile outer = moduleWithDevenvNix("outer");
        VirtualFile inner = createChildDirectory(outer, "inner");
        createChildData(inner, "devenv.nix");
        PsiTestUtil.addContentRoot(createModule("inner"), inner);

        assertEquals(List.of(outer, inner), DevenvCli.findDevenvRoots(getProject()));
        assertEquals(outer, DevenvCli.findDevenvRoot(getProject()));
    }

    /** What the per-file features go through: the environment declared closest to the file. */
    public void testAFileBelongsToTheNearestRoot() {
        VirtualFile outer = moduleWithDevenvNix("outer");
        VirtualFile inner = createChildDirectory(outer, "inner");
        createChildData(inner, "devenv.nix");
        PsiTestUtil.addContentRoot(createModule("inner"), inner);

        assertEquals(outer, DevenvCli.findDevenvRootFor(getProject(), createChildData(outer, "shell.nix")));
        assertEquals(inner, DevenvCli.findDevenvRootFor(getProject(), createChildData(inner, "shell.nix")));
        assertEquals(inner, DevenvCli.findDevenvRootFor(getProject(), inner.findChild("devenv.nix")));
    }

    public void testAFileOutsideEveryRootBelongsToNone() {
        moduleWithDevenvNix("first");
        VirtualFile elsewhere = createChildDirectory(getOrCreateProjectBaseDir(), "plain");

        assertNull(DevenvCli.findDevenvRootFor(getProject(), createChildData(elsewhere, "shell.nix")));
    }

    public void testAModuleWithoutADevenvNixIsNoRoot() {
        PsiTestUtil.addContentRoot(createModule("plain"), createChildDirectory(getOrCreateProjectBaseDir(), "plain"));

        assertEmpty(DevenvCli.findDevenvRoots(getProject()));
        assertNull(DevenvCli.findDevenvRoot(getProject()));
    }
}
