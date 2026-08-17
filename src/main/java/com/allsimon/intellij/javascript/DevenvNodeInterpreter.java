package com.allsimon.intellij.javascript;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef;
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter;
import com.intellij.javascript.nodejs.npm.NpmManager;
import com.intellij.javascript.nodejs.npm.NpmUtil;
import com.intellij.javascript.nodejs.util.NodePackage;
import com.intellij.javascript.nodejs.util.NodePackageRef;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sets the Node.js interpreter of a devenv project, and its package manager, to the ones devenv
 * declares.
 * <p>
 * A project that sets {@code languages.javascript.enable} has already said which Node.js it runs on,
 * and a terminal in that project gets exactly that one; left alone, the IDE would go looking for a
 * Node.js on the machine instead - a system install, an nvm version, whatever it finds first - and
 * run the project's tooling on a runtime nobody chose. The same goes for the npm, pnpm or yarn
 * declared next to it. As with Maven, there is one such setting for the whole project, so setting it
 * at startup and on every later devenv.nix change is enough.
 * <p>
 * Both are used from the moment they are set: run configurations and the JavaScript tooling read them
 * through {@link NodeJsInterpreterManager} and {@link NpmManager}, and nothing has to be reloaded for
 * them to be picked up.
 * <p>
 * The package manager is only ever set, never cleared: a project that stops declaring one keeps
 * whatever it had, because nothing here can tell a manager this service set from one the user picked
 * by hand, and clearing the wrong one would be worse than leaving a stale one behind.
 */
@Service(Service.Level.PROJECT)
final class DevenvNodeInterpreter implements Disposable {
    private static final Logger LOG = Logger.getInstance(DevenvNodeInterpreter.class);

    private final Project project;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean listening = new AtomicBoolean();

    /** What the user was last told about, so only a genuine change is worth a balloon. */
    private volatile Path notifiedInterpreter;
    private volatile Path notifiedPackageManager;

    DevenvNodeInterpreter(@NotNull Project project) {
        this.project = project;
    }

    static @NotNull DevenvNodeInterpreter getInstance(@NotNull Project project) {
        return project.getService(DevenvNodeInterpreter.class);
    }

    /**
     * Sets the interpreter now, and again on every later devenv.nix change. Does nothing at all
     * outside a devenv project, so no process is ever spawned for one.
     */
    void start() {
        if (project.isDisposed() || DevenvCli.findDevenvRoot(project) == null) {
            return;
        }
        if (listening.compareAndSet(false, true)) {
            project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                @Override
                public void after(@NotNull List<? extends VFileEvent> events) {
                    for (VFileEvent event : events) {
                        VirtualFile file = event.getFile();
                        if (file != null && DevenvCli.isConfigurationFile(file)) {
                            configureAsync();
                            return;
                        }
                    }
                }
            });
        }
        configureAsync();
    }

    private void configureAsync() {
        if (project.isDisposed() || !inFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            new Task.Backgroundable(project, MyMessageBundle.message("javascript.devenv.progress"), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        configure();
                    } catch (RuntimeException e) {
                        LOG.warn("Failed to configure the Node.js interpreter from devenv", e);
                    } finally {
                        inFlight.set(false);
                    }
                }
            }.queue();
        } catch (RuntimeException e) {
            // Nothing will clear the flag if queueing itself failed, and a flag left set would freeze
            // every later attempt.
            inFlight.set(false);
            throw e;
        }
    }

    private void configure() {
        VirtualFile root = DevenvCli.findDevenvRoot(project);
        File executable = DevenvCli.findExecutable();
        if (root == null || executable == null) {
            return;
        }

        Path interpreter = DevenvJavascript.resolveInterpreter(executable, root);
        if (interpreter == null) {
            // The normal case for a project that doesn't enable 'languages.javascript', and the case
            // of one whose environment has never been built.
            return;
        }
        if (!new NodeJsLocalInterpreter(interpreter.toString()).isValid()) {
            // The path was checked as it was resolved, so this only fires if the IDE and we ever
            // disagree on what makes a runnable interpreter - better a warning than one it refuses.
            LOG.warn("devenv declares a Node.js at " + interpreter + ", which the IDE does not accept");
            return;
        }

        // Only once there is a runtime to run it with: the IDE drives npm, pnpm and yarn by running
        // their CLI on the Node.js interpreter, so a package manager without one is of no use to it.
        Path packageManager = DevenvJavascript.resolvePackageManager(executable, root);

        ApplicationManager.getApplication()
                .invokeLater(() -> apply(interpreter, packageManager), project.getDisposed());
    }

    private void apply(@NotNull Path interpreter, @Nullable Path packageManager) {
        if (project.isDisposed()) {
            return;
        }

        // Both, before deciding whether to say anything: a change to either is worth the one balloon.
        boolean changed = applyInterpreter(interpreter);
        changed |= applyPackageManager(packageManager);
        if (!changed || (interpreter.equals(notifiedInterpreter)
                && Objects.equals(packageManager, notifiedPackageManager))) {
            return;
        }

        notifiedInterpreter = interpreter;
        notifiedPackageManager = packageManager;
        if (packageManager == null) {
            notify(MyMessageBundle.message("javascript.devenv.configured"),
                    MyMessageBundle.message("javascript.devenv.configuredDetails", interpreter.toString()));
        } else {
            notify(MyMessageBundle.message("javascript.devenv.configuredWithPackageManager"),
                    MyMessageBundle.message("javascript.devenv.configuredWithPackageManagerDetails",
                            interpreter.toString(), packageManager.toString()));
        }
    }

    private boolean applyInterpreter(@NotNull Path interpreter) {
        NodeJsInterpreterManager manager = NodeJsInterpreterManager.getInstance(project);
        // A reference to this exact path, not the project-level one: that is the very setting being
        // filled in here, and referring to it would point the project at itself.
        NodeJsInterpreterRef wanted = NodeJsInterpreterRef.create(new NodeJsLocalInterpreter(interpreter.toString()));
        NodeJsInterpreterRef current = manager.getInterpreterRef();
        if (wanted.equals(current)) {
            return false;
        }

        LOG.info("Setting the Node.js interpreter to " + interpreter + ", was " + current.getReferenceName());
        manager.setInterpreterRef(wanted);
        return true;
    }

    private boolean applyPackageManager(@Nullable Path packageManager) {
        if (packageManager == null) {
            // A project that declares none keeps whatever it has, which is the IDE's own detection
            // from the lock file - a better answer than anything that could be put there instead.
            return false;
        }

        // The npm descriptor rather than a bare package: it is the one that knows how to run a package
        // directory as a package manager, and it reads the manager's name back off the path, which is
        // how the IDE decides whether it is driving npm, pnpm or yarn.
        NodePackage wanted = NpmUtil.DESCRIPTOR.createPackage(packageManager.toString());
        NpmManager manager = NpmManager.getInstance(project);
        NodePackageRef current = manager.getPackageRef();
        if (NodePackageRef.create(wanted).equals(current)) {
            return false;
        }

        LOG.info("Setting the package manager to " + wanted.getName() + " at " + packageManager + ", was "
                + current.getReferenceName());
        manager.setPackageRef(NodePackageRef.create(wanted));
        return true;
    }

    private void notify(@NotNull String title, @NotNull String details) {
        if (project.isDisposed()) {
            return;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Devenv")
                .createNotification(title, details, NotificationType.INFORMATION)
                .notify(project);
    }

    @Override
    public void dispose() {
    }
}
