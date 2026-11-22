package com.allsimon.intellij.nixupdate;

import com.allsimon.intellij.nixlang.NixUpdates;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.affogato.lsp.Updates;
import org.affogato.update.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The packages of the project's {@code .nix} files, with their version and the latest one, and actions to update
 * them or refresh their hashes.
 */
public final class NixPackagesPanel extends SimpleToolWindowPanel implements Disposable {
    /** A package, and what is known of its latest version: null while it is looked for. */
    public static final class Row {
        final Workspace.Found found;
        volatile @Nullable String latest;

        Row(Workspace.Found found, @Nullable String latest) {
            this.found = found;
            this.latest = latest;
        }

        public String name() {
            return found.name();
        }

        public @Nullable String latest() {
            return latest;
        }
    }

    private final Project project;
    private final ListTableModel<Row> model = new ListTableModel<>(
            column("Package", Row::name),
            column("Version", r -> r.found.pkg().version() == null ? "" : r.found.pkg().version()),
            column("Latest", r -> r.latest == null ? "…" : r.latest),
            column("File", r -> relative(r.found.file())));
    private final TableView<Row> table = new TableView<>(model);
    private final AtomicInteger scans = new AtomicInteger();
    private volatile boolean disposed;

    public NixPackagesPanel(Project project) {
        super(true, true);
        this.project = project;
        table.setShowGrid(false);
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                Row row = table.getSelectedObject();
                if (row == null) {
                    return false;
                }
                VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(row.found.file());
                if (file != null) {
                    new OpenFileDescriptor(project, file, row.found.pkg().start()).navigate(true);
                }
                return true;
            }
        }.installOn(table);
        DefaultActionGroup actions = new DefaultActionGroup(
                action("Reload", "Find the packages again, and their latest version", AllIcons.Actions.Refresh,
                        false, this::scan),
                action("Update", "Update the selected packages to their latest version", AllIcons.Actions.Download,
                        true, () -> update(false)),
                action("Refresh Hashes", "Refresh the hashes of the selected packages at their version",
                        AllIcons.Actions.ForceRefresh, true, () -> update(true)),
                action("Update All", "Update every package below the project, as affogato update does",
                        AllIcons.Actions.Execute, false, () -> {
                            List<Path> roots = NixUpdates.roots(project);
                            if (!roots.isEmpty()) {
                                NixUpdateWorkspaceAction.run(project, roots, this::scan);
                            }
                        }));
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("NixPackages", actions, true);
        toolbar.setTargetComponent(table);
        setToolbar(toolbar.getComponent());
        setContent(ScrollPaneFactory.createScrollPane(table));
    }

    private static ColumnInfo<Row, String> column(String name, java.util.function.Function<Row, String> value) {
        return new ColumnInfo<>(name) {
            @Override
            public @Nullable String valueOf(Row row) {
                return value.apply(row);
            }
        };
    }

    private DumbAwareAction action(String text, String description, javax.swing.Icon icon, boolean needsSelection,
                                   Runnable run) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                event.getPresentation().setEnabled(!needsSelection || !table.getSelectedObjects().isEmpty());
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                run.run();
            }
        };
    }

    private String relative(Path file) {
        for (Path root : NixUpdates.roots(project)) {
            if (file.startsWith(root)) {
                return root.relativize(file).toString();
            }
        }
        return file.toString();
    }

    @TestOnly
    public List<Row> rows() {
        return List.copyOf(model.getItems());
    }

    /** Finds the packages below the project, then their latest versions, in the background. */
    public void scan() {
        List<Path> roots = NixUpdates.roots(project);
        if (roots.isEmpty()) {
            return;
        }
        int scan = scans.incrementAndGet();
        FileDocumentManager.getInstance().saveAllDocuments();
        new Task.Backgroundable(project, "Finding the Nix packages", true) {
            private List<Row> rows;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Workspace.Discovery discovery;
                try {
                    discovery = NixUpdates.cancellable(indicator, () -> Workspace.find(roots, true));
                } catch (IOException | RuntimeException e) {
                    rows = List.of();
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                rows = new ArrayList<>();
                for (Workspace.Found f : discovery.found()) {
                    rows.add(new Row(f, f.pkg().updatable(false) ? null : "(" + f.pkg().skipped() + ")"));
                }
            }

            @Override
            public void onSuccess() {
                if (rows == null || disposed || scans.get() != scan) {
                    return;
                }
                model.setItems(new ArrayList<>(rows));
                latest(scan, rows);
            }
        }.queue();
    }

    /** Asks for the latest version of each package that can be updated, a few at a time. */
    private void latest(int scan, List<Row> rows) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try (ExecutorService pool = Executors.newFixedThreadPool(4, Thread.ofVirtual().factory())) {
                for (Row row : rows) {
                    if (row.latest != null) {
                        continue;
                    }
                    pool.submit(() -> {
                        if (disposed || scans.get() != scan) {
                            return;
                        }
                        String latest;
                        try {
                            String newer = NixUpdates.newer(row.found.pkg());
                            latest = newer != null ? newer : "up to date";
                        } catch (IOException | RuntimeException e) {
                            latest = "(" + e.getMessage() + ")";
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        row.latest = latest;
                        ApplicationManager.getApplication().invokeLater(() -> {
                            int i = model.indexOf(row);
                            if (!disposed && i >= 0) {
                                model.fireTableRowsUpdated(i, i);
                            }
                        });
                    });
                }
            }
        });
    }

    /** Updates the selected packages, or refreshes their hashes, one after the other, then finds them again. */
    private void update(boolean refreshing) {
        List<Row> selected = List.copyOf(table.getSelectedObjects());
        if (selected.isEmpty()) {
            return;
        }
        FileDocumentManager.getInstance().saveAllDocuments();
        new Task.Backgroundable(project, refreshing ? "Refreshing the hashes" : "Updating the packages", true) {
            private final List<String> failures = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                for (Row row : selected) {
                    Workspace.Found f = row.found;
                    indicator.setText(f.name());
                    String text = text(f.file());
                    if (text == null) {
                        continue;
                    }
                    Updates.Progress progress = (step, done) -> {
                        indicator.setText2(step);
                        indicator.setFraction(done);
                    };
                    try {
                        Updates.Update update = NixUpdates.cancellable(indicator, () -> refreshing
                                ? NixUpdates.updater().refresh(f.file(), text, f.index(), progress)
                                : NixUpdates.updater().latest(f.file(), text, f.index(), progress));
                        if (update == null || update.edits().isEmpty() && update.files().isEmpty()) {
                            continue;
                        }
                        String title = refreshing ? "Refresh the hashes of " + f.name() : "Update " + f.name();
                        boolean[] written = new boolean[1];
                        ApplicationManager.getApplication().invokeAndWait(
                                () -> written[0] = NixUpdates.apply(project, title, f.file(), text, update));
                        if (!written[0]) {
                            failures.add(f.name() + ": the file changed meanwhile");
                        }
                    } catch (ProcessCanceledException e) {
                        throw e;
                    } catch (IOException | RuntimeException e) {
                        failures.add(f.name() + ": " + e.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            @Override
            public void onFinished() {
                if (!failures.isEmpty()) {
                    com.intellij.notification.NotificationGroupManager.getInstance().getNotificationGroup("Devenv")
                            .createNotification(refreshing ? "Refreshing the hashes failed" : "Updating failed",
                                    String.join("<br>", failures),
                                    com.intellij.notification.NotificationType.WARNING)
                            .notify(project);
                }
                scan();
            }
        }.queue();
    }

    /** The text of {@code file}, from its document. */
    private static @Nullable String text(Path file) {
        return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<String>) () -> {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByNioFile(file);
            Document document = vf == null ? null : FileDocumentManager.getInstance().getDocument(vf);
            return document == null ? null : document.getText();
        });
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
