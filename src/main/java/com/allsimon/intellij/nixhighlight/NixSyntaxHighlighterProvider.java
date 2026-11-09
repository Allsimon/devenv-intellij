package com.allsimon.intellij.nixhighlight;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Colors the {@code .nix} files the IDE still opens as text, as when the user associated them with Text. Any other
 * file falls through to the next provider.
 */
public final class NixSyntaxHighlighterProvider implements SyntaxHighlighterProvider {
    @Override
    public @Nullable SyntaxHighlighter create(FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
        return file != null && "nix".equals(file.getExtension()) ? new NixSyntaxHighlighter() : null;
    }
}
