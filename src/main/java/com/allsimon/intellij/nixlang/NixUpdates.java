package com.allsimon.intellij.nixlang;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.affogato.lint.Edit;
import org.affogato.lint.Packages.Package;
import org.affogato.lsp.Updates;
import org.affogato.update.Updater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * affogato's updater, made once with Nix's programs from the IDE's PATH, for the intentions, the annotator of
 * outdated packages, the workspace action and the tool window; replaced in tests.
 */
public final class NixUpdates {
    private NixUpdates() {}

    /** The newest version of a package, or null when it has it. */
    public interface Newer {
        @Nullable String version(Package p) throws IOException, InterruptedException;
    }

    /** What updates the packages below some roots, without writing them. */
    public interface Workspace {
        Updates.WorkspaceUpdate update(List<Path> roots, Updates.Progress progress)
                throws IOException, InterruptedException;
    }

    public interface Work<T> {
        T run() throws IOException, InterruptedException;
    }

    private static Updater updater;
    private static Newer newer;
    private static Workspace workspace;

    public static synchronized Updater updater() {
        if (updater == null) {
            Updater.Options defaults = Updater.Options.defaults();
            updater = new Updater(new Updater.Options(defaults.githubApi(), defaults.githubToken(),
                    program(defaults.prefetcher()), program(defaults.nixBuild()), program(defaults.nix()),
                    defaults.nixpkgs()));
        }
        return updater;
    }

    /** The program's path on the IDE's PATH, else its name, for the process to look it up. */
    private static String program(String name) {
        File found = PathEnvironmentVariableUtil.findInPath(name);
        return found != null ? found.getAbsolutePath() : name;
    }

    /**
     * The newest version of {@code p}, kept a while, or null when it has it; null as well in tests that did not
     * {@link #replace} it, which must not reach the network.
     */
    public static @Nullable String newer(Package p) throws IOException, InterruptedException {
        Newer hook;
        synchronized (NixUpdates.class) {
            hook = newer;
        }
        if (hook != null) {
            return hook.version(p);
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return null;
        }
        Updater.Newer n = updater().cachedNewer(p);
        return n == null ? null : n.version();
    }

    public static Updates.WorkspaceUpdate updateWorkspace(List<Path> roots, Updates.Progress progress)
            throws IOException, InterruptedException {
        Workspace hook;
        synchronized (NixUpdates.class) {
            hook = workspace;
        }
        return hook != null ? hook.update(roots, progress) : updater().updateWorkspace(roots, progress);
    }

    /**
     * Runs {@code work} on a thread of its own until it ends, or until {@code indicator} is cancelled: the thread is
     * then interrupted, which stops the fetch or build it runs, and this throws {@link ProcessCanceledException}.
     */
    public static <T> T cancellable(@NotNull ProgressIndicator indicator, Work<T> work)
            throws IOException, InterruptedException {
        CompletableFuture<T> result = new CompletableFuture<>();
        Thread thread = Thread.ofVirtual().name("affogato-update").start(() -> {
            try {
                result.complete(work.run());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        while (true) {
            try {
                return result.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (indicator.isCanceled()) {
                    thread.interrupt();
                    throw new ProcessCanceledException();
                }
            } catch (ExecutionException e) {
                switch (e.getCause()) {
                    case IOException io -> throw io;
                    case InterruptedException interrupted -> throw interrupted;
                    case RuntimeException runtime -> throw runtime;
                    case Error error -> throw error;
                    default -> throw new IOException(e.getCause());
                }
            }
        }
    }

    /**
     * Writes, in one command, the edits of {@code update} to the document of {@code file}, unless its text is no
     * longer {@code before}, and the other files it changes; whether it wrote them. To be called on the EDT.
     */
    public static boolean apply(Project project, String title, Path file, String before, Updates.Update update) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(file);
        Document document = virtualFile == null ? null : FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null || !document.getText().equals(before)) {
            return false;
        }
        List<Edit> edits = new ArrayList<>(update.edits());
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        WriteCommandAction.runWriteCommandAction(project, title, null, () -> {
            PsiDocumentManager.getInstance(project).commitDocument(document);
            for (Edit e : edits) {
                document.replaceString(e.start(), e.end(), e.replacement());
            }
            PsiDocumentManager.getInstance(project).commitDocument(document);
            writeFiles(update.files());
        });
        return true;
    }

    /**
     * Writes the new text of each file, through its document, unless it no longer has the text the change started
     * from; the paths of those it left. To be called in a write action.
     */
    public static List<String> writeFiles(Map<String, Updates.Change> files) {
        List<String> left = new ArrayList<>();
        for (Map.Entry<String, Updates.Change> e : files.entrySet()) {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByNioFile(Path.of(e.getKey()));
            Document document = vf == null ? null : FileDocumentManager.getInstance().getDocument(vf);
            if (document == null || !document.getText().equals(e.getValue().before())) {
                left.add(e.getKey());
                continue;
            }
            document.setText(e.getValue().after());
        }
        return left;
    }

    /** The content roots of the project on the local file system, else its base directory. */
    public static List<Path> roots(Project project) {
        List<Path> roots = new ArrayList<>();
        for (VirtualFile root : com.intellij.openapi.roots.ProjectRootManager.getInstance(project).getContentRoots()) {
            Path path = root.getFileSystem().getNioPath(root);
            if (path != null) {
                roots.add(path);
            }
        }
        if (roots.isEmpty() && project.getBasePath() != null) {
            roots.add(Path.of(project.getBasePath()));
        }
        return roots;
    }

    @TestOnly
    public static synchronized void replace(@Nullable Newer newerVersion, @Nullable Workspace updateWorkspace) {
        newer = newerVersion;
        workspace = updateWorkspace;
    }
}
