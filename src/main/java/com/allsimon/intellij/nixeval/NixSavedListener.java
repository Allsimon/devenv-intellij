package com.allsimon.intellij.nixeval;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Tells the evaluators that a {@code .nix} file changed on disk, so the values read from it are evaluated again. */
public final class NixSavedListener implements BulkFileListener {
    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
            if (!(event instanceof VFileContentChangeEvent) || !event.getPath().endsWith(".nix")) {
                continue;
            }
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                NixEvaluatorService service = project.getServiceIfCreated(NixEvaluatorService.class);
                if (service != null) {
                    service.saved(event.getPath());
                }
            }
        }
    }
}
