package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.affogato.lint.Edit;
import org.affogato.lint.Packages;
import org.affogato.lint.Packages.Package;
import org.affogato.lsp.Updates;
import org.affogato.lsp.Updates.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Updates the package around the caret, fetched from a forge, a registry or a URL, to its latest version with
 * affogato's updater: its version, the hash of its source, its lock files and the hashes of its dependencies, which
 * Nix builds. As {@link NixRefreshHashesIntention}, it refreshes those hashes at the version the package has instead.
 */
public class NixUpdatePackageIntention extends PsiElementBaseIntentionAction {
    /** What updates a package, or refreshes its hashes: {@link org.affogato.update.Updater#latest} or {@link org.affogato.update.Updater#refresh}, but for tests. */
    interface Work {
        @Nullable Update run(Path file, String text, int index, Updates.Progress progress)
                throws IOException, InterruptedException;
    }

    private static Work latest;
    private static Work refresh;

    private final boolean refreshing;

    public NixUpdatePackageIntention() {
        this(false);
    }

    protected NixUpdatePackageIntention(boolean refreshing) {
        this.refreshing = refreshing;
    }

    /** A package of a file on disk, which the builds of its dependencies need. */
    record Target(Path file, String text, int index, Package pkg) {
        String name() {
            return pkg.name() != null ? pkg.name() : "the package";
        }
    }

    @Override
    public @NotNull String getFamilyName() {
        return refreshing ? "Refresh the hashes of the package" : "Update the package";
    }

    private String title(String name) {
        return refreshing ? "Refresh the hashes of " + name : "Update " + name;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    /** None: it would fetch and build to show it. */
    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor,
                                                         @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Target target = target(element, refreshing);
        if (target == null) {
            return false;
        }
        setText(title(target.name()));
        return true;
    }

    /**
     * The package around {@code element} that can be updated, or whose hashes can be refreshed when
     * {@code refreshing}, as those of one pinned to a commit can, in a file on disk.
     */
    static @Nullable Target target(PsiElement element, boolean refreshing) {
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getOriginalFile().getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
            return null;
        }
        String text = file.getText();
        if (!text.contains("fetch")) {
            return null;
        }
        List<Package> packages;
        try {
            packages = Packages.find(text);
        } catch (RuntimeException e) {
            return null;
        }
        int offset = element.getTextOffset();
        for (int i = 0; i < packages.size(); i++) {
            Package p = packages.get(i);
            boolean can = refreshing ? !p.sources().isEmpty() : p.updatable(false);
            if (can && p.start() <= offset && offset <= p.end()) {
                return new Target(virtualFile.toNioPath(), text, i, p);
            }
        }
        return null;
    }

    /**
     * Updates in the background, then writes the edits in a command of their own, unless the document changed
     * meanwhile: they are offsets in the text the update started from.
     */
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Target target = target(element, refreshing);
        PsiFile file = element.getContainingFile();
        if (target == null || !FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        Document document = editor.getDocument();
        long stamp = document.getModificationStamp();
        String running = (refreshing ? "Refreshing the hashes of " : "Updating ") + target.name();
        new Task.Backgroundable(project, running, true) {
            private Update update;
            private String error;
            private boolean cancelled;

            /** Shows each step; once cancelled, stops the fetch or build under way. */
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                indicator.setFraction(0);
                Updates.Progress progress = (step, done) -> {
                    indicator.setText2(step);
                    indicator.setFraction(done);
                };
                try {
                    Work work = work(refreshing);
                    update = NixUpdates.cancellable(indicator,
                            () -> work.run(target.file(), target.text(), target.index(), progress));
                } catch (ProcessCanceledException e) {
                    cancelled = true;
                    throw e;
                } catch (IOException | RuntimeException e) {
                    error = String.valueOf(e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error = "interrupted";
                }
            }

            @Override
            public void onFinished() {
                if (cancelled) {
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() -> {
                    String name = target.name();
                    String version = target.pkg().version();
                    if (error != null) {
                        hint(editor, running + " failed: " + error, true);
                    } else if (update == null || update.edits().isEmpty()) {
                        hint(editor, refreshing ? "The hashes of " + name + " are up to date"
                                : name + " " + version + " is up to date", false);
                    } else if (document.getModificationStamp() != stamp) {
                        hint(editor, "Not changing " + name + ": the file changed meanwhile", true);
                    } else if (FileModificationService.getInstance().prepareFileForWrite(file)) {
                        WriteCommandAction.runWriteCommandAction(project, title(name), null, () -> {
                            write(project, document, update.edits());
                            NixUpdates.writeFiles(update.files());
                        });
                        hint(editor, (refreshing ? "Refreshed the hashes of " + name
                                : name + ": " + version + " -> " + update.version())
                                + (update.notUpdated().isEmpty() ? ""
                                : " (" + String.join(", ", update.notUpdated()) + " not updated)"), false);
                    }
                });
            }
        }.queue();
    }

    private static void write(Project project, Document document, List<Edit> edits) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        edits.stream().sorted(Comparator.comparingInt(Edit::start).reversed())
                .forEach(e -> document.replaceString(e.start(), e.end(), e.replacement()));
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    private static void hint(Editor editor, String message, boolean error) {
        if (editor.isDisposed()) {
            return;
        }
        if (error) {
            HintManager.getInstance().showErrorHint(editor, message);
        } else {
            HintManager.getInstance().showInformationHint(editor, message);
        }
    }

    private static synchronized Work work(boolean refreshing) {
        if (refreshing) {
            return refresh != null ? refresh : NixUpdates.updater()::refresh;
        }
        return latest != null ? latest : NixUpdates.updater()::latest;
    }

    @TestOnly
    static synchronized void replace(@Nullable Work update, @Nullable Work refreshHashes) {
        latest = update;
        refresh = refreshHashes;
    }
}
