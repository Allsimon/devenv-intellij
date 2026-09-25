package com.allsimon.intellij.lsp;

import com.allsimon.intellij.core.DevenvProjectTestCase;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Which files a server answers for in a project holding several devenv.nix: its own root's, and only
 * those, so a .nix file is never described by another module's devenv.
 */
public class DevenvLspRootsTest extends DevenvProjectTestCase {

    public void testAServerTakesOnlyTheNixFilesOfItsOwnRoot() {
        VirtualFile first = moduleWithDevenvNix("first");
        VirtualFile second = moduleWithDevenvNix("second");
        DevenvLspServerDescriptor descriptor = new DevenvLspServerDescriptor(getProject(), first);

        assertTrue(descriptor.isSupportedFile(createChildData(first, "shell.nix")));
        assertFalse(descriptor.isSupportedFile(createChildData(second, "shell.nix")));
        assertFalse(descriptor.isSupportedFile(createChildData(first, "build.gradle.kts")));
    }

    /** A file held by two roots belongs to the innermost, so exactly one server takes it. */
    public void testANestedRootTakesItsOwnFilesFromTheRootAroundIt() {
        VirtualFile outer = moduleWithDevenvNix("outer");
        VirtualFile inner = createChildDirectory(outer, "inner");
        createChildData(inner, "devenv.nix");
        PsiTestUtil.addContentRoot(createModule("inner"), inner);
        VirtualFile nested = createChildData(inner, "shell.nix");

        assertFalse(new DevenvLspServerDescriptor(getProject(), outer).isSupportedFile(nested));
        assertTrue(new DevenvLspServerDescriptor(getProject(), inner).isSupportedFile(nested));
    }

    public void testStartsNixdDirectlyWhenItIsOnPath() throws Exception {
        VirtualFile root = moduleWithDevenvNix("root");
        DevenvLspServerDescriptor descriptor = new DevenvLspServerDescriptor(getProject(), root);

        GeneralCommandLine commandLine = descriptor.createCommandLine(
                new File("/tools/devenv"), new File("/tools/nixd"));

        assertEquals(new File("/tools/nixd").getAbsolutePath(), commandLine.getExePath());
        assertTrue(commandLine.getParametersList().getList().isEmpty());
        assertEquals(root.getPath(), commandLine.getWorkDirectory().getPath());
    }

    public void testFallsBackToDevenvLspWhenNixdIsNotOnPath() throws Exception {
        VirtualFile root = moduleWithDevenvNix("root");
        DevenvLspServerDescriptor descriptor = new DevenvLspServerDescriptor(getProject(), root);

        GeneralCommandLine commandLine = descriptor.createCommandLine(new File("/tools/devenv"), null);

        assertEquals(new File("/tools/devenv").getAbsolutePath(), commandLine.getExePath());
        assertEquals(java.util.List.of("lsp"), commandLine.getParametersList().getList());
        assertEquals(root.getPath(), commandLine.getWorkDirectory().getPath());
    }
}
