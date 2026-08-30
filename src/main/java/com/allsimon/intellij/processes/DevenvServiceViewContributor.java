package com.allsimon.intellij.processes;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewGroupingContributor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Contributes the processes declared in a project's devenv.nix files to the Services tool window, so
 * they can be started, stopped and inspected without dropping into a terminal.
 * <p>
 * A project holding several devenv.nix - modules attached side by side, or a repository whose modules
 * each carry one - contributes the processes of all of them, under a node per root. A project holding
 * a single one lists its processes with no such node in between, which is what
 * {@link #getGroups(DevenvProcessNode)} returning nothing means.
 * <p>
 * The platform hides a contributor whose service list is empty, which is exactly what should happen
 * for projects that aren't devenv projects at all.
 */
public final class DevenvServiceViewContributor
        implements ServiceViewGroupingContributor<DevenvProcessNode, DevenvProcessManager> {

    /** Tells the Services tool window to re-read what this contributor has to show. */
    static void notifyServicesChanged(@NotNull Project project) {
        project.getMessageBus()
                .syncPublisher(ServiceEventListener.TOPIC)
                .handle(ServiceEventListener.ServiceEvent.createResetEvent(DevenvServiceViewContributor.class));
    }

    @Override
    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return DevenvProcessRoots.getInstance(project).rootDescriptor();
    }

    @Override
    public @NotNull List<DevenvProcessNode> getServices(@NotNull Project project) {
        List<DevenvProcessNode> services = new ArrayList<>();
        for (DevenvProcessManager manager : DevenvProcessRoots.getInstance(project).managers()) {
            for (String name : manager.getProcessNames()) {
                services.add(new DevenvProcessNode(manager, name));
            }
        }
        return services;
    }

    /**
     * Processes are grouped under their devenv root only when the project has more than one; the
     * single-root project - the common one - lists them the way it did before there was anything to
     * tell apart.
     */
    @Override
    public @NotNull List<DevenvProcessManager> getGroups(@NotNull DevenvProcessNode service) {
        return DevenvProcessRoots.getInstance(service.manager().getProject()).hasSeveralRoots()
                ? List.of(service.manager())
                : List.of();
    }

    @Override
    public @NotNull ServiceViewDescriptor getGroupDescriptor(@NotNull DevenvProcessManager manager) {
        return manager.rootDescriptor();
    }

    @Override
    public @NotNull ServiceViewDescriptor getServiceDescriptor(@NotNull Project project,
                                                               @NotNull DevenvProcessNode service) {
        return service.manager().descriptorFor(service.name());
    }
}
