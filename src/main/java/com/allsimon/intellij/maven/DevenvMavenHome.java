package com.allsimon.intellij.maven;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
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
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenHomeType;
import org.jetbrains.idea.maven.project.MavenInSpecificPath;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sets the Maven home path of a devenv project to the Maven devenv declares.
 * <p>
 * A project that sets {@code languages.java.maven.enable} has already said which Maven it builds
 * with, and a terminal in that project gets exactly that one; left alone, the IDE would use its
 * bundled Maven instead. Unlike Gradle, there is one such setting for the whole project rather than
 * one per linked build, so setting it at startup and on every later devenv.nix change is enough.
 * <p>
 * The change takes effect at the next Maven reload; nothing here triggers one, since a reimport is
 * the user's to ask for.
 */
@Service(Service.Level.PROJECT)
final class DevenvMavenHome implements Disposable {
    private static final Logger LOG = Logger.getInstance(DevenvMavenHome.class);

    private final Project project;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean listening = new AtomicBoolean();

    /** The home the user was last told about, so only a genuine change is worth a balloon. */
    private volatile Path notifiedHome;

    DevenvMavenHome(@NotNull Project project) {
        this.project = project;
    }

    static @NotNull DevenvMavenHome getInstance(@NotNull Project project) {
        return project.getService(DevenvMavenHome.class);
    }

    /**
     * Sets the Maven home now, and again on every later devenv.nix change. Does nothing at all
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
            new Task.Backgroundable(project, MyMessageBundle.message("maven.devenv.progress"), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        configure();
                    } catch (RuntimeException e) {
                        LOG.warn("Failed to configure the Maven home from devenv", e);
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

        Path home = DevenvMaven.resolveHome(executable, root);
        if (home == null) {
            // The normal case for a project that doesn't enable 'languages.java.maven', and the case
            // of one whose environment has never been built.
            return;
        }
        if (!MavenUtil.isValidMavenHome(home)) {
            // The candidate search found the marker the IDE looks for, so this only fires if the two
            // ever disagree - better a warning than a Maven home the IDE will refuse.
            LOG.warn("devenv declares a Maven at " + home + ", which the IDE does not accept as a home");
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> apply(home), project.getDisposed());
    }

    private void apply(@NotNull Path home) {
        if (project.isDisposed()) {
            return;
        }

        MavenGeneralSettings settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings()
                .getGeneralSettings();
        MavenHomeType wanted = new MavenInSpecificPath(home.toString());
        if (wanted.equals(settings.getMavenHomeType())) {
            return;
        }

        LOG.info("Setting the Maven home to " + home + ", was " + settings.getMavenHomeType());
        settings.setMavenHomeType(wanted);

        if (!home.equals(notifiedHome)) {
            notifiedHome = home;
            notify(MyMessageBundle.message("maven.devenv.configured"),
                    MyMessageBundle.message("maven.devenv.configuredDetails", home.toString()));
        }
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
