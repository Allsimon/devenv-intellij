package com.allsimon.intellij.core;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Keeps the files of the Nix store, as those go to declaration opens in nixpkgs or devenv, read-only: the store can
 * not be written, so offering to make one writable only leads to a failing save.
 */
public final class NixStoreWritingAccessProvider extends WritingAccessProvider {
    private static final String STORE = "/nix/store/";

    static boolean isInStore(@NotNull String path) {
        return path.startsWith(STORE);
    }

    @Override
    public @NotNull Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
        return List.copyOf(files.stream().filter(this::isStoreFile).toList());
    }

    @Override
    public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
        return !isStoreFile(file);
    }

    @Override
    public String getReadOnlyMessage() {
        return MyMessageBundle.message("store.readOnly");
    }

    private boolean isStoreFile(VirtualFile file) {
        return file.isInLocalFileSystem() && isInStore(file.getPath());
    }
}
