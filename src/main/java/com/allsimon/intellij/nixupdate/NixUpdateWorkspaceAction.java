package com.allsimon.intellij.nixupdate;

import com.allsimon.intellij.nixlang.NixUpdates;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.affogato.lsp.Updates;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates every package of the selected files and directories, else of the project, as {@code affogato update} does,
 * then writes the files it changed through their documents, in one command.
 */
public final class NixUpdateWorkspaceAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && !roots(event).isEmpty());
    }

    private static List<Path> roots(AnActionEvent event) {
        List<Path> roots = new ArrayList<>();
        VirtualFile[] selected = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (selected != null) {
            for (VirtualFile f : selected) {
                if (f.isInLocalFileSystem() && (f.isDirectory() || "nix".equals(f.getExtension()))) {
                    roots.add(f.toNioPath());
                }
            }
        }
        Project project = event.getProject();
        if (roots.isEmpty() && project != null) {
            roots.addAll(NixUpdates.roots(project));
        }
        return roots;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        List<Path> roots = roots(event);
        if (project == null || roots.isEmpty()) {
            return;
        }
        run(project, roots, () -> {});
    }

    /** Updates the packages below {@code roots} in the background, then writes them and runs {@code then}. */
    public static void run(Project project, List<Path> roots, Runnable then) {
        FileDocumentManager.getInstance().saveAllDocuments();
        new Task.Backgroundable(project, "Updating the Nix packages", true) {
            private Updates.WorkspaceUpdate update;
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                Updates.Progress progress = (step, done) -> {
                    indicator.setText2(step);
                    indicator.setFraction(done);
                };
                try {
                    update = NixUpdates.cancellable(indicator, () -> NixUpdates.updateWorkspace(roots, progress));
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (IOException | RuntimeException e) {
                    error = String.valueOf(e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error = "interrupted";
                }
            }

            @Override
            public void onSuccess() {
                if (error != null) {
                    show(project, "Updating the Nix packages failed", error, NotificationType.ERROR);
                    return;
                }
                if (update == null) {
                    return;
                }
                List<String> left = new ArrayList<>();
                WriteCommandAction.runWriteCommandAction(project, "Update the Nix packages", null,
                        () -> left.addAll(NixUpdates.writeFiles(update.files())));
                List<String> lines = new ArrayList<>(update.summary());
                for (String f : left) {
                    lines.add(f + ": not changed, as it changed meanwhile");
                }
                boolean failed = lines.stream().anyMatch(l -> l.contains(": error: "));
                show(project, lines.isEmpty() ? "Every Nix package is up to date" : "Nix packages updated",
                        String.join("<br>", lines.stream().map(NixUpdateWorkspaceAction::escape).toList()),
                        failed ? NotificationType.WARNING : NotificationType.INFORMATION);
                then.run();
            }
        }.queue();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void show(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup("Devenv")
                .createNotification(title, content, type)
                .notify(project);
    }
}
