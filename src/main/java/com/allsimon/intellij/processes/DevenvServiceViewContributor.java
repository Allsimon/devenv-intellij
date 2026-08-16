package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvCli;
import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contributes the processes declared in a project's devenv.nix to the Services tool window, so they
 * can be started, stopped and inspected without dropping into a terminal.
 * <p>
 * The platform hides a contributor whose service list is empty, which is exactly what should happen
 * for projects that aren't devenv projects at all.
 */
public final class DevenvServiceViewContributor implements ServiceViewContributor<DevenvProcess> {

    @Override
    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new DevenvRootServiceViewDescriptor(DevenvProcessManager.getInstance(project));
    }

    @Override
    public @NotNull List<DevenvProcess> getServices(@NotNull Project project) {
        if (DevenvCli.findDevenvRoot(project) == null) {
            return List.of();
        }
        return DevenvProcessManager.getInstance(project).getSnapshot();
    }

    @Override
    public @NotNull ServiceViewDescriptor getServiceDescriptor(@NotNull Project project,
                                                               @NotNull DevenvProcess process) {
        return DevenvProcessManager.getInstance(project).descriptorFor(process);
    }
}
