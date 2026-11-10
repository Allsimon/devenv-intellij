package com.allsimon.intellij.core;

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Keeps the files of the Nix store, as those go to declaration opens in nixpkgs or devenv, read-only: the store can
 * not be written, so offering to make one writable only leads to a failing save.
 * <p>
 * The platform's provider for files outside the project comes first and would ask whether to edit one anyway, so
 * {@link Outside} lets store files past it, for this provider to refuse.
 */
public final class NixStoreWritingAccessProvider extends WritingAccessProvider {
    public static final class Outside implements NonProjectFileWritingAccessExtension {
        @Override
        public boolean isWritable(@NotNull VirtualFile file) {
            return isStoreFile(file);
        }
    }

    private static final String STORE = "/nix/store/";

    static boolean isInStore(@NotNull String path) {
        return path.startsWith(STORE);
    }

    @Override
    public @NotNull Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
        return List.copyOf(files.stream().filter(NixStoreWritingAccessProvider::isStoreFile).toList());
    }

    @Override
    public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
        return !isStoreFile(file);
    }

    @Override
    public String getReadOnlyMessage() {
        return MyMessageBundle.message("store.readOnly");
    }

    private static boolean isStoreFile(VirtualFile file) {
        return file.isInLocalFileSystem() && isInStore(file.getPath());
    }
}
