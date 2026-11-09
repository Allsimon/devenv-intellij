package com.allsimon.intellij.nixlang;

import com.allsimon.intellij.nixeval.NixEvaluatorService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.affogato.lsp.Analysis;
import org.affogato.parser.SyntaxTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** affogato's {@link Analysis} of a Nix file, over its syntax tree and the project's other Nix files. */
public final class NixAnalyses {
    private NixAnalyses() {}

    /** The analysis of {@code file}, with the evaluator unless switched off; in a read action. */
    public static @NotNull Analysis of(@NotNull PsiFile file) {
        return analysis(file, NixEvaluatorService.getInstance(file.getProject()).evaluator());
    }

    /** The analysis of {@code file} for what needs no evaluation. */
    public static @NotNull Analysis syntax(@NotNull PsiFile file) {
        return analysis(file, null);
    }

    private static Analysis analysis(PsiFile file, @Nullable org.affogato.lsp.Evaluator evaluator) {
        PsiFile original = file.getOriginalFile();
        return new Analysis(path(original), original.getText(), tree(original), evaluator, new Workspace(original.getProject()));
    }

    static @NotNull SyntaxTree tree(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(
                file, () -> CachedValueProvider.Result.create(SyntaxTree.parse(file.getText()), file));
    }

    static @Nullable String path(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile != null && virtualFile.isInLocalFileSystem() ? virtualFile.getPath() : null;
    }

    static @Nullable PsiFile file(@NotNull Project project, @NotNull String path) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
        return virtualFile == null || virtualFile.isDirectory() ? null : PsiManager.getInstance(project).findFile(virtualFile);
    }

    /** The element at {@code line} and {@code column}, counted from 0, of the file at {@code path}. */
    static @Nullable PsiElement at(@NotNull Project project, @NotNull String path, int line, int column) {
        PsiFile file = file(project, path);
        Document document = file == null ? null : FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document == null || line < 0 || line >= document.getLineCount()) {
            return file;
        }
        int offset = Math.min(document.getLineStartOffset(line) + column, document.getLineEndOffset(line));
        PsiElement element = file.findElementAt(offset);
        return element != null ? element : file;
    }

    private record Workspace(Project project) implements Analysis.Workspace {
        @Override
        public SyntaxTree tree(String path) {
            PsiFile file = file(project, path);
            return file == null ? null : NixAnalyses.tree(file);
        }

        @Override
        public Map<String, SyntaxTree> files() {
            Map<String, SyntaxTree> files = new LinkedHashMap<>();
            if (DumbService.isDumb(project)) {
                return files;
            }
            for (VirtualFile file : FileTypeIndex.getFiles(NixFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
                SyntaxTree tree = tree(file.getPath());
                if (tree != null) {
                    files.put(file.getPath(), tree);
                }
            }
            return files;
        }
    }
}
