package com.allsimon.intellij.jdk;

import com.allsimon.intellij.core.DevenvFeature;
import com.allsimon.intellij.core.DevenvSettings;
import com.allsimon.intellij.core.DevenvSettingsListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Sets the Project SDK of the projects that are already open when the feature is switched back on,
 * so the change doesn't wait for the next devenv.nix edit.
 * <p>
 * Switching it off needs nothing here: the service simply stops evaluating, and the SDK it last set
 * stays until the user changes it.
 */
public final class DevenvJdkSettingsListener implements DevenvSettingsListener {

    @Override
    public void settingsChanged(@NotNull Set<DevenvFeature> changed) {
        if (!changed.contains(DevenvFeature.JDK)
                || !DevenvSettings.getInstance().isEnabled(DevenvFeature.JDK)) {
            return;
        }

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                // Idempotent: outside a devenv project it does nothing at all, and in one it only
                // re-subscribes what isn't subscribed yet.
                DevenvProjectSdk.getInstance(project).start();
            }
        }
    }
}
