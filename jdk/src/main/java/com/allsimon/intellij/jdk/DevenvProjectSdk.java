package com.allsimon.intellij.jdk;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the Project SDK on the JDK the project's devenv.nix declares.
 * <p>
 * A devenv project states its toolchain in devenv.nix, so the IDE has no business asking the user to
 * pick a JDK by hand: {@code languages.java.enable} means "this project builds with that JDK", and
 * this service makes the Project SDK say the same thing - at startup and again whenever devenv.nix
 * changes. A JDK the user selected by hand is therefore overwritten, on purpose.
 * <p>
 * Only the Project SDK is touched. A Gradle or Maven build keeps compiling with whatever JVM its own
 * settings name, which the plugin deliberately leaves alone - but importing that build does get a say
 * over the Project SDK, and it has the last word: it runs minutes after this service has had its turn
 * at startup, and a build that provisions its own toolchain JDK points the project at that one and
 * rewrites misc.xml accordingly. Setting the SDK once at startup is therefore not enough; the SDK is
 * watched and put back whenever something moves it, which is also what makes a JDK chosen by hand in
 * Project Structure revert.
 */
@Service(Service.Level.PROJECT)
final class DevenvProjectSdk implements Disposable {
    private static final Logger LOG = Logger.getInstance(DevenvProjectSdk.class);

    private final Project project;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean listening = new AtomicBoolean();

    /** The JDK home of the last successful evaluation, so re-applying it costs no devenv call. */
    private volatile String resolvedHome;
    /** The JDK home the user was last told about, so only a genuine change is worth a balloon. */
    private volatile String notifiedHome;

    DevenvProjectSdk(@NotNull Project project) {
        this.project = project;
    }

    static @NotNull DevenvProjectSdk getInstance(@NotNull Project project) {
        return project.getService(DevenvProjectSdk.class);
    }

    /**
     * Configures the Project SDK now, re-evaluates it on every later devenv.nix change, and puts it
     * back whenever something else moves it. Does nothing at all outside a devenv project, so no
     * process is ever spawned for one.
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

            // Setting the SDK once is not enough: importing a Gradle or Maven build assigns the
            // Project SDK itself, minutes after the startup activity that first brought us here, and
            // an import that provisions a toolchain JDK will happily point the project at that one.
            // Watching the roots catches it whoever the culprit is - the import, its follow-up tasks,
            // or the reload of a misc.xml the import has just rewritten - because they all end up
            // firing this event. The re-application is a no-op unless the SDK really did move away,
            // so the event costs nothing on the many root changes that have nothing to do with SDKs.
            connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
                @Override
                public void rootsChanged(@NotNull ModuleRootEvent event) {
                    reapply();
                }
            });

            // The import is still worth a trigger of its own: it is the one moment we know the SDK
            // may have been reassigned, and it fires even when the assignment changed no root.
            connection.subscribe(ProjectDataImportListener.TOPIC, new ProjectDataImportListener() {
                @Override
                public void onImportFinished(@Nullable String projectPath) {
                    reapply();
                }

                /** The import's own follow-up work runs after onImportFinished, and can set the SDK too. */
                @Override
                public void onFinalTasksFinished(@Nullable String projectPath) {
                    reapply();
                }
            });
        }
        configureAsync();
    }

    /**
     * Puts the JDK of the last evaluation back, without asking devenv again, and without touching the
     * write lock when the Project SDK is the devenv one already - which is the case on all but a
     * handful of the root changes this is called from.
     */
    private void reapply() {
        String home = resolvedHome;
        if (home == null || project.isDisposed()) {
            return;
        }
        if (isDevenvSdk(ProjectRootManager.getInstance(project).getProjectSdk(), home)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> apply(home), project.getDisposed());
    }

    private boolean isDevenvSdk(@Nullable Sdk sdk, @NotNull String home) {
        return sdk != null && sdkName().equals(sdk.getName()) && home.equals(sdk.getHomePath());
    }

    private void configureAsync() {
        if (project.isDisposed() || !inFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            new Task.Backgroundable(project, MyMessageBundle.message("jdk.devenv.progress"), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        configure();
                    } catch (RuntimeException e) {
                        LOG.warn("Failed to configure the Project SDK from devenv", e);
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

        String home = DevenvJdk.evaluateHome(executable, root);
        if (home == null) {
            // The normal case for a project that doesn't enable 'languages.java'.
            resolvedHome = null;
            return;
        }
        if (!JavaSdk.getInstance().isValidSdkHome(home)) {
            // 'devenv eval' only evaluates: it hands back the store path the JDK *would* have, which
            // isn't there until something builds it.
            LOG.warn("devenv declares a JDK at " + home + ", which is not a usable JDK home");
            notifyMissing();
            resolvedHome = null;
            return;
        }

        resolvedHome = home;
        ApplicationManager.getApplication().invokeLater(() -> apply(home), project.getDisposed());
    }

    private void apply(@NotNull String home) {
        if (project.isDisposed()) {
            return;
        }

        String name = sdkName();
        boolean[] changed = {false};
        WriteAction.run(() -> {
            ProjectJdkTable table = ProjectJdkTable.getInstance();
            Sdk sdk = table.findJdk(name);
            if (sdk != null && !home.equals(sdk.getHomePath())) {
                // A rebuilt environment moves the JDK to a new store path. Replacing the entry rather
                // than editing its roots in place keeps a single 'devenv (<project>)' JDK in the table
                // however often the environment is updated.
                table.removeJdk(sdk);
                sdk = null;
            }
            if (sdk == null) {
                sdk = JavaSdk.getInstance().createJdk(name, home, false);
                table.addJdk(sdk);
            }

            ProjectRootManager roots = ProjectRootManager.getInstance(project);
            Sdk current = roots.getProjectSdk();
            if (!sdk.equals(current)) {
                LOG.info("Setting the Project SDK to '" + name + "' (" + home + "), was "
                        + (current == null ? "unset" : "'" + current.getName() + "'"));
                roots.setProjectSdk(sdk);
                changed[0] = true;
            }
        });

        // Only the first time a given JDK is set: putting the SDK back after an import that took it
        // away is routine, and a balloon for each of those would be noise.
        if (changed[0] && !home.equals(notifiedHome)) {
            notifiedHome = home;
            notify(MyMessageBundle.message("jdk.devenv.configured"), home, NotificationType.INFORMATION);
        }
    }

    /**
     * Scoped to the project, so two devenv projects open side by side each keep their own JDK and
     * neither leaves a second entry behind when its environment is rebuilt.
     */
    private @NotNull String sdkName() {
        return MyMessageBundle.message("jdk.devenv.sdkName", project.getName());
    }

    private void notifyMissing() {
        notify(MyMessageBundle.message("jdk.devenv.notBuilt"), MyMessageBundle.message("jdk.devenv.notBuiltDetails"),
                NotificationType.WARNING);
    }

    private void notify(@NotNull String title, @NotNull String details, @NotNull NotificationType type) {
        if (project.isDisposed()) {
            return;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Devenv")
                .createNotification(title, details, type)
                .notify(project);
    }

    @Override
    public void dispose() {
    }
}
