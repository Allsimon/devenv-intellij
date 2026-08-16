package com.allsimon.intellij.gradle;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Points the linked Gradle builds of a devenv project at the Gradle, and the JVM, devenv declares.
 * <p>
 * A project that sets {@code languages.java.gradle.enable} has already said which Gradle it builds
 * with, and a terminal in that project gets exactly that one. Left alone, the IDE would instead run
 * the wrapper, download a second Gradle of its own and build with it - so every linked build under
 * the devenv root is switched to the local distribution devenv provides, and to the Project SDK,
 * which is the JDK devenv declares. A build is linked when a project is first imported, which is
 * well after this service has run, so builds are also caught as they arrive; later syncs keep
 * whichever settings are configured and need no watching.
 * <p>
 * The change takes effect at the next Gradle reload; nothing here triggers one, since a sync is the
 * user's to ask for.
 * <p>
 * A build declaring a Java toolchain still resolves that toolchain the Gradle way, which may well
 * mean downloading a JDK of its own: that is the build's decision, not a setting of the IDE, and
 * nothing here overrides it.
 */
@Service(Service.Level.PROJECT)
final class DevenvGradleDistribution implements Disposable {
    private static final Logger LOG = Logger.getInstance(DevenvGradleDistribution.class);

    private final Project project;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean listening = new AtomicBoolean();

    /** The Gradle home of the last successful evaluation, so re-applying it costs no devenv call. */
    private volatile Path resolvedHome;
    /** The home the user was last told about, so only a genuine change is worth a balloon. */
    private volatile Path notifiedHome;

    DevenvGradleDistribution(@NotNull Project project) {
        this.project = project;
    }

    static @NotNull DevenvGradleDistribution getInstance(@NotNull Project project) {
        return project.getService(DevenvGradleDistribution.class);
    }

    /**
     * Configures the linked builds now, re-evaluates on every later devenv.nix change, and configures
     * builds linked after that. Does nothing at all outside a devenv project, so no process is ever
     * spawned for one.
     */
    void start() {
        if (project.isDisposed() || DevenvCli.findDevenvRoot(project) == null) {
            return;
        }
        if (listening.compareAndSet(false, true)) {
            MessageBusConnection connection = project.getMessageBus().connect(this);

            connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
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

            // A project opened for the first time has no linked build yet: it is linked once the
            // import is under way, with whatever distribution the import chose - the wrapper, for a
            // project that has one. Both events are needed: 'linked' for a build being added, 'loaded'
            // for the builds a reopened project restores from gradle.xml.
            connection.subscribe(GradleSettingsListener.TOPIC, new GradleSettingsListener() {
                @Override
                public void onProjectsLinked(@NotNull Collection<GradleProjectSettings> settings) {
                    reapply();
                }

                @Override
                public void onProjectsLoaded(@NotNull Collection<GradleProjectSettings> settings) {
                    reapply();
                }
            });
        }
        configureAsync();
    }

    /** Re-applies the Gradle of the last evaluation, without asking devenv again. */
    private void reapply() {
        Path home = resolvedHome;
        if (home != null && !project.isDisposed()) {
            ApplicationManager.getApplication().invokeLater(() -> apply(home), project.getDisposed());
        }
    }

    private void configureAsync() {
        if (project.isDisposed() || !inFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            new Task.Backgroundable(project, MyMessageBundle.message("gradle.devenv.progress"), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        configure();
                    } catch (RuntimeException e) {
                        LOG.warn("Failed to configure the Gradle distribution from devenv", e);
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

        Path home = DevenvGradle.resolveHome(executable, root);
        if (home == null) {
            // The normal case for a project that doesn't enable 'languages.java.gradle', and the case
            // of one whose environment has never been built.
            resolvedHome = null;
            return;
        }

        resolvedHome = home;
        ApplicationManager.getApplication().invokeLater(() -> apply(home), project.getDisposed());
    }

    private void apply(@NotNull Path home) {
        if (project.isDisposed()) {
            return;
        }

        VirtualFile root = DevenvCli.findDevenvRoot(project);
        if (root == null) {
            return;
        }

        String path = home.toString();
        boolean changed = false;
        for (GradleProjectSettings settings : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
            // A build linked from outside the devenv root - another checkout opened as a second
            // linked project - has nothing to do with this devenv.nix and keeps its own distribution.
            if (!isUnder(settings.getExternalProjectPath(), root)) {
                continue;
            }
            if (DistributionType.LOCAL == settings.getDistributionType() && path.equals(settings.getGradleHome())
                    && ExternalSystemJdkUtil.USE_PROJECT_JDK.equals(settings.getGradleJvm())) {
                continue;
            }
            LOG.info("Pointing the Gradle build at " + settings.getExternalProjectPath() + " to " + path
                    + " on the Project SDK, was " + settings.getDistributionType() + " " + settings.getGradleHome()
                    + " on " + settings.getGradleJvm());
            settings.setDistributionType(DistributionType.LOCAL);
            settings.setGradleHome(path);
            // The Project SDK rather than the JDK's path or the name of the SDK holding it: that SDK
            // belongs to the module that reads 'languages.java', which this one knows nothing about,
            // and the indirection keeps the two settings from ever disagreeing. '#JAVA_HOME' would do
            // the same job only when the IDE was started from inside the devenv shell.
            settings.setGradleJvm(ExternalSystemJdkUtil.USE_PROJECT_JDK);
            changed = true;
        }

        // Only the first time a given Gradle is set: putting the distribution back after an import
        // that took it away is routine, and a balloon for each of those would be noise.
        if (changed && !home.equals(notifiedHome)) {
            notifiedHome = home;
            notify(MyMessageBundle.message("gradle.devenv.configured"),
                    MyMessageBundle.message("gradle.devenv.configuredDetails", path));
        }
    }

    private static boolean isUnder(@Nullable String externalProjectPath, @NotNull VirtualFile devenvRoot) {
        return externalProjectPath != null && Path.of(externalProjectPath).startsWith(Path.of(devenvRoot.getPath()));
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
