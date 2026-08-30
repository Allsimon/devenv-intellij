package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvCli;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The devenv roots of a project, each with the {@link DevenvProcessManager} that runs devenv in it.
 * <p>
 * A project can have several - modules attached side by side, or a repository whose modules each
 * carry a devenv.nix - and devenv runs one process manager per environment, so nothing here is
 * shared between roots but the watching of the files that invalidate them.
 */
@Service(Service.Level.PROJECT)
final class DevenvProcessRoots implements Disposable {
    private final Project project;
    /** Managers by root path, in the order {@link DevenvCli#findDevenvRoots} lists the roots. */
    private final Map<String, DevenvProcessManager> managers = new LinkedHashMap<>();

    private DevenvRootServiceViewDescriptor rootDescriptor;

    DevenvProcessRoots(@NotNull Project project) {
        this.project = project;
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                onConfigurationChanged(events);
            }
        });
    }

    static @NotNull DevenvProcessRoots getInstance(@NotNull Project project) {
        return project.getService(DevenvProcessRoots.class);
    }

    /**
     * One manager per devenv root of the project, created here and disposed as soon as the root they
     * were created for is gone.
     */
    @NotNull List<DevenvProcessManager> managers() {
        if (project.isDisposed()) {
            return List.of();
        }
        // Searched for outside the lock: it needs read access, and a background thread waiting for that
        // while holding the lock would deadlock against the write action this can be called from.
        List<VirtualFile> roots = DevenvCli.findDevenvRoots(project);

        List<DevenvProcessManager> current;
        List<DevenvProcessManager> stale;
        synchronized (this) {
            Map<String, DevenvProcessManager> previous = new LinkedHashMap<>(managers);
            managers.clear();
            for (VirtualFile root : roots) {
                DevenvProcessManager manager = previous.remove(root.getPath());
                if (manager == null) {
                    manager = new DevenvProcessManager(project, root);
                    Disposer.register(this, manager);
                }
                managers.put(root.getPath(), manager);
            }
            current = List.copyOf(managers.values());
            // Whatever is left is a root that lost its devenv.nix, or a module that was detached.
            stale = List.copyOf(previous.values());
        }

        if (!stale.isEmpty()) {
            // The consoles hanging off a manager are UI, so they are let go of on the EDT rather than on
            // whichever thread happened to ask for the roots.
            ApplicationManager.getApplication()
                    .invokeLater(() -> stale.forEach(Disposer::dispose), project.getDisposed());
        }
        return current;
    }

    /**
     * Whether the project has more than one devenv root, as of the last {@link #managers()} - which is
     * what the tool window asks for its services before it asks how to group them.
     */
    synchronized boolean hasSeveralRoots() {
        return managers.size() > 1;
    }

    /**
     * The 'Devenv' node, whose commands act on every root at once. One per project, because the
     * platform asks for it again on every model rebuild and a descriptor handing out fresh toolbar
     * actions each time is an error the action system reports.
     */
    synchronized @NotNull DevenvRootServiceViewDescriptor rootDescriptor() {
        if (rootDescriptor == null) {
            rootDescriptor = DevenvRootServiceViewDescriptor.of(this);
        }
        return rootDescriptor;
    }

    /**
     * A devenv.nix or devenv.lock that changed makes its own root re-read its declarations; one that
     * appeared or vanished changes the set of roots, which only a reset of the tree picks up.
     */
    private void onConfigurationChanged(@NotNull List<? extends VFileEvent> events) {
        Set<String> changedRoots = new HashSet<>();
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file != null && DevenvCli.isConfigurationFile(file)) {
                VirtualFile parent = file.getParent();
                if (parent != null) {
                    changedRoots.add(parent.getPath());
                }
            }
        }
        if (changedRoots.isEmpty() || project.isDisposed()) {
            return;
        }

        Set<String> before = rootPaths();
        for (DevenvProcessManager manager : managers()) {
            if (changedRoots.contains(manager.getRoot().getPath())) {
                manager.reload();
            }
        }
        // Only when the roots themselves changed: a reset rebuilds the tree and drops the selection,
        // and what a saved devenv.nix does to the processes is published by the root that re-read it.
        if (!before.equals(rootPaths())) {
            DevenvServiceViewContributor.notifyServicesChanged(project);
        }
    }

    private synchronized @NotNull Set<String> rootPaths() {
        return Set.copyOf(managers.keySet());
    }

    @Override
    public synchronized void dispose() {
        // The managers are registered children of this service, so the platform disposes them; only the
        // map has to be let go of.
        managers.clear();
    }
}
