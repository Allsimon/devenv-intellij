package com.allsimon.intellij.core;

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NixStoreWritingAccessTest extends BasePlatformTestCase {
    public void testRefusesAStoreFileWithoutAskingWhetherToEditIt() {
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), "/nix/store");
        VirtualFile file = storeFile();
        if (file == null) {
            return;
        }
        NonProjectFileWritingAccessProvider.enableChecksInTests(true, getTestRootDisposable());

        assertTrue(NonProjectFileWritingAccessProvider.isWriteAccessAllowed(file, getProject()));
        assertFalse(WritingAccessProvider.isPotentiallyWritable(file, getProject()));
        assertTrue(ReadonlyStatusHandler.getInstance(getProject()).ensureFilesWritable(java.util.List.of(file))
                .hasReadonlyFiles());
    }

    /** A file of the store from the PATH, which the devenv shell the tests run in fills with store directories. */
    private static VirtualFile storeFile() {
        for (String dir : System.getenv().getOrDefault("PATH", "").split(":")) {
            if (!NixStoreWritingAccessProvider.isInStore(dir)) {
                continue;
            }
            java.io.File[] files = new java.io.File(dir).listFiles(java.io.File::isFile);
            if (files != null && files.length > 0) {
                return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(files[0]);
            }
        }
        return null;
    }
}
