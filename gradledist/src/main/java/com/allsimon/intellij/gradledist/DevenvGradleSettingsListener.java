package com.allsimon.intellij.gradledist;

import com.allsimon.intellij.core.DevenvFeature;
import com.allsimon.intellij.core.DevenvSettings;
import com.allsimon.intellij.core.DevenvSettingsListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Points the linked builds of the projects that are already open back at the devenv Gradle when the
 * feature is switched on, so the change doesn't wait for the next devenv.nix edit.
 * <p>
 * Switching it off needs nothing here: the service simply stops evaluating, and the distribution it
 * last set stays until the user changes it.
 */
public final class DevenvGradleSettingsListener implements DevenvSettingsListener {

    @Override
    public void settingsChanged(@NotNull Set<DevenvFeature> changed) {
        if (!changed.contains(DevenvFeature.GRADLE)
                || !DevenvSettings.getInstance().isEnabled(DevenvFeature.GRADLE)) {
            return;
        }

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                // Idempotent: outside a devenv project it does nothing at all, and in one it only
                // re-subscribes what isn't subscribed yet.
                DevenvGradleDistribution.getInstance(project).start();
            }
        }
    }
}
