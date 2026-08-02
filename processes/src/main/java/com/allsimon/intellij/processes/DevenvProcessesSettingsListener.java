package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.DevenvFeature;
import com.allsimon.intellij.core.DevenvSettingsListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Adds the devenv node to - or removes it from - the Services tool window of the projects that are
 * already open when the feature is switched.
 */
public final class DevenvProcessesSettingsListener implements DevenvSettingsListener {

    @Override
    public void settingsChanged(@NotNull Set<DevenvFeature> changed) {
        if (!changed.contains(DevenvFeature.PROCESSES)) {
            return;
        }

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            // Only for devenv projects: elsewhere the manager has never run and must not be created
            // just to be told about a feature it would do nothing with.
            if (!project.isDisposed() && DevenvCli.findDevenvRoot(project) != null) {
                DevenvProcessManager.getInstance(project).featureToggled();
            }
        }
    }
}
