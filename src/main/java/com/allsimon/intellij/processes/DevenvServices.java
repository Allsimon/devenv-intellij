package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvCli;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** Finds a process the Services tool window lists, from a file of its devenv root. */
public final class DevenvServices {
    private DevenvServices() {
    }

    /** Whether the Services view lists {@code name} for the devenv root holding {@code file}. */
    public static boolean lists(@NotNull Project project, @NotNull VirtualFile file, @NotNull String name) {
        return node(project, file, name) != null;
    }

    /** Selects the process in the Services view, opening it. */
    public static void show(@NotNull Project project, @NotNull VirtualFile file, @NotNull String name) {
        DevenvProcessNode node = node(project, file, name);
        if (node != null) {
            ServiceViewManager.getInstance(project).select(node, DevenvServiceViewContributor.class, true, true);
        }
    }

    private static DevenvProcessNode node(Project project, VirtualFile file, String name) {
        VirtualFile root = DevenvCli.findDevenvRootFor(project, file);
        if (root == null) {
            return null;
        }
        for (DevenvProcessManager manager : DevenvProcessRoots.getInstance(project).managers()) {
            if (manager.getRoot().equals(root) && manager.getProcessNames().contains(name)) {
                return new DevenvProcessNode(manager, name);
            }
        }
        return null;
    }
}
