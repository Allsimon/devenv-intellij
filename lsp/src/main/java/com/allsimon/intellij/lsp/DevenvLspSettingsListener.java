package com.allsimon.intellij.lsp;

import com.allsimon.intellij.core.DevenvFeature;
import com.allsimon.intellij.core.DevenvSettings;
import com.allsimon.intellij.core.DevenvSettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.platform.lsp.api.LspServerManager;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Starts or stops the language servers of the projects that are already open when the feature is
 * switched, so the change doesn't wait for the next Nix file to be opened.
 */
public final class DevenvLspSettingsListener implements DevenvSettingsListener {

    @Override
    public void settingsChanged(@NotNull Set<DevenvFeature> changed) {
        if (!changed.contains(DevenvFeature.LSP)) {
            return;
        }

        boolean enabled = DevenvSettings.getInstance().isEnabled(DevenvFeature.LSP);
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) {
                    continue;
                }
                LspServerManager manager = LspServerManager.getInstance(project);
                if (enabled) {
                    // Asks the provider again for every open file, so a server appears for the Nix
                    // files already on screen.
                    manager.startServersIfNeeded(DevenvLspServerSupportProvider.class);
                } else {
                    manager.stopServers(DevenvLspServerSupportProvider.class);
                }
            }
        });
    }
}
