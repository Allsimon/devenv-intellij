package com.allsimon.intellij.nixhighlight;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Colors the {@code .nix} files the IDE opens as text, registered for the plain and auto-detected text file types
 * rather than giving Nix a file type of its own, which would clash with a Nix plugin's. Any other file falls
 * through to the next provider.
 */
public final class NixSyntaxHighlighterProvider implements SyntaxHighlighterProvider {
    @Override
    public @Nullable SyntaxHighlighter create(FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
        return file != null && "nix".equals(file.getExtension()) ? new NixSyntaxHighlighter() : null;
    }
}
