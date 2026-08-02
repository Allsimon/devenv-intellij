package com.allsimon.intellij.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Applies a change to the '.devenv' exclusion to the projects that are already open.
 * <p>
 * {@link DevenvExcludePolicy} is only asked for its URLs when the project roots change, so switching
 * the feature has to say that they did; the rescan that follows is what actually indexes '.devenv'
 * again, or drops it from the index.
 */
public final class DevenvExcludeSettingsListener implements DevenvSettingsListener {

    @Override
    public void settingsChanged(@NotNull Set<DevenvFeature> changed) {
        if (!changed.contains(DevenvFeature.EXCLUDE)) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed() || DevenvCli.findDevenvRoot(project) == null) {
                    continue;
                }
                WriteAction.run(() -> ProjectRootManagerEx.getInstanceEx(project)
                        .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN));
            }
        });
    }
}
