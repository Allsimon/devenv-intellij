package com.allsimon.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.services.ServiceEventListener;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Everything the Services tool window knows about a project's devenv processes: which ones
 * devenv.nix declares, what the running process manager reports about them, and the commands that
 * change that.
 * <p>
 * State lives here rather than in {@link DevenvServiceViewContributor} because the contributor is
 * application-wide and its {@code getServices} runs on the UI path - it may only ever read
 * {@link #getSnapshot()}, never spawn a process.
 */
@Service(Service.Level.PROJECT)
final class DevenvProcessManager implements Disposable {
    private static final Logger LOG = Logger.getInstance(DevenvProcessManager.class);

    private static final long POLL_INTERVAL_SECONDS = 5;
    // 'devenv processes list' only talks to the already-running manager - it answered in ~80ms in
    // every state we measured - so a short timeout here keeps a hung manager from stalling the poll.
    private static final int LIST_TIMEOUT_MILLIS = 10_000;
    private static final int LOGS_TIMEOUT_MILLIS = 30_000;

    private final Project project;
    private final Map<String, DevenvProcessServiceViewDescriptor> descriptors = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();
    private final AtomicBoolean pollingStarted = new AtomicBoolean();

    private volatile List<DevenvProcess> snapshot = List.of();
    /** Result of the last {@code devenv eval processes}; {@code null} until it has run at least once. */
    private volatile List<DevenvProcess> declared;
    private volatile ScheduledFuture<?> poller;

    DevenvProcessManager(@NotNull Project project) {
        this.project = project;
    }

    static @NotNull DevenvProcessManager getInstance(@NotNull Project project) {
        return project.getService(DevenvProcessManager.class);
    }

    /** The processes to show, as of the last refresh. Never blocks. */
    @NotNull List<DevenvProcess> getSnapshot() {
        startPolling();
        return snapshot;
    }

    @NotNull Project getProject() {
        return project;
    }

    @NotNull DevenvProcessServiceViewDescriptor descriptorFor(@NotNull DevenvProcess process) {
        DevenvProcessServiceViewDescriptor descriptor =
                descriptors.computeIfAbsent(process.name(), name -> new DevenvProcessServiceViewDescriptor(this, name));
        // Descriptors are cached per name so that a poll-driven tree reset doesn't throw away the
        // console (and the selection) of a node that merely changed status.
        descriptor.setProcess(process);
        return descriptor;
    }

    @Nullable DevenvProcess findProcess(@NotNull String name) {
        for (DevenvProcess process : snapshot) {
            if (process.name().equals(name)) {
                return process;
            }
        }
        return null;
    }

    /**
     * Polling only starts once something actually asks for the services of a devenv project, so
     * non-devenv projects never spawn anything.
     */
    private void startPolling() {
        if (project.isDisposed() || DevenvCli.findDevenvRoot(project) == null) {
            return;
        }
        if (!pollingStarted.compareAndSet(false, true)) {
            return;
        }

        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file != null && DevenvCli.isConfigurationFile(file)) {
                        reload();
                        return;
                    }
                }
            }
        });

        poller = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                this::poll, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** scheduleWithFixedDelay stops rescheduling as soon as one run throws, so nothing may escape here. */
    private void poll() {
        try {
            refreshAsync();
        } catch (RuntimeException e) {
            LOG.warn("Failed to schedule a devenv process refresh", e);
        }
    }

    /** Re-reads process status, and the devenv.nix declarations too if they were never read. */
    void refreshAsync() {
        if (project.isDisposed() || !refreshInFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            if (declared == null) {
                // The first 'devenv eval processes' has to evaluate the whole devenv configuration, which
                // takes minutes against a cold nix eval cache - worth a visible progress indicator.
                new Task.Backgroundable(project, MyMessageBundle.message("services.devenv.loading"), false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        refreshAndRelease();
                    }
                }.queue();
            } else {
                AppExecutorUtil.getAppExecutorService().execute(this::refreshAndRelease);
            }
        } catch (RuntimeException e) {
            // Nothing will call refreshAndRelease if the dispatch itself failed, and a flag left set
            // would freeze every later refresh.
            refreshInFlight.set(false);
            throw e;
        }
    }

    /** Drops the cached devenv.nix declarations, then refreshes. */
    void reload() {
        declared = null;
        refreshAsync();
    }

    private void refreshAndRelease() {
        try {
            refresh();
        } catch (RuntimeException e) {
            LOG.warn("Failed to refresh devenv processes", e);
        } finally {
            refreshInFlight.set(false);
        }
    }

    private void refresh() {
        VirtualFile root = DevenvCli.findDevenvRoot(project);
        File executable = DevenvCli.findExecutable();
        if (root == null || executable == null) {
            publish(List.of());
            return;
        }

        List<DevenvProcess> declaredProcesses = declared;
        if (declaredProcesses == null) {
            declaredProcesses = evaluateDeclared(executable, root);
            declared = declaredProcesses;
        }
        publish(merge(declaredProcesses, listReported(executable, root)));
    }

    /**
     * The declared processes take priority for ordering and for {@code exec}; a process the manager
     * reports but devenv.nix no longer declares is still shown, so a stale manager doesn't hide
     * something the user can stop.
     */
    private static @NotNull List<DevenvProcess> merge(@NotNull List<DevenvProcess> declaredProcesses,
                                                      @NotNull Map<String, DevenvProcess> reported) {
        Map<String, DevenvProcess> remaining = new LinkedHashMap<>(reported);
        List<DevenvProcess> merged = new ArrayList<>(declaredProcesses.size() + remaining.size());
        for (DevenvProcess process : declaredProcesses) {
            DevenvProcess runtimeState = remaining.remove(process.name());
            merged.add(runtimeState == null ? process : process.withRuntimeState(runtimeState));
        }
        merged.addAll(remaining.values());
        return List.copyOf(merged);
    }

    private @NotNull List<DevenvProcess> evaluateDeclared(@NotNull File executable, @NotNull VirtualFile root) {
        ProcessOutput output = execute(DevenvCli.commandLine(executable, root.getPath(), "eval", "processes"), 0);
        if (output == null) {
            return List.of();
        }
        if (output.getExitCode() != 0) {
            LOG.warn("'devenv eval processes' exited with " + output.getExitCode() + ": " + output.getStderr());
            return List.of();
        }
        try {
            return DevenvProcessParser.parseDeclared(output.getStdout());
        } catch (RuntimeException e) {
            LOG.warn("Failed to parse 'devenv eval processes' output", e);
            return List.of();
        }
    }

    private @NotNull Map<String, DevenvProcess> listReported(@NotNull File executable, @NotNull VirtualFile root) {
        ProcessOutput output =
                execute(DevenvCli.commandLine(executable, root.getPath(), "processes", "list"), LIST_TIMEOUT_MILLIS);
        // A non-zero exit is the normal way devenv says "no process manager is running", so it is not
        // worth a warning - it just means every declared process is still in its NOT_STARTED state.
        if (output == null || output.getExitCode() != 0) {
            return Map.of();
        }
        return DevenvProcessParser.parseList(output.getStdout());
    }

    /** Reads the tail of a process's log; returns {@code null} when devenv could not be run at all. */
    @Nullable String readLogs(@NotNull String processName, int lines) {
        VirtualFile root = DevenvCli.findDevenvRoot(project);
        File executable = DevenvCli.findExecutable();
        if (root == null || executable == null) {
            return null;
        }

        GeneralCommandLine commandLine = DevenvCli.commandLine(
                executable, root.getPath(), "processes", "logs", processName, "-n", String.valueOf(lines));
        ProcessOutput output = execute(commandLine, LOGS_TIMEOUT_MILLIS);
        if (output == null) {
            return null;
        }
        String stdout = output.getStdout();
        return output.getExitCode() == 0 && !stdout.isBlank() ? stdout : DevenvCli.stripAnsi(output.getStderr());
    }

    /**
     * Runs one of the process-control commands in the background and refreshes afterwards, reporting
     * failures as a balloon rather than swallowing them.
     */
    void runCommand(@NotNull String title, String @NotNull ... arguments) {
        VirtualFile root = DevenvCli.findDevenvRoot(project);
        File executable = DevenvCli.findExecutable();
        if (executable == null) {
            notifyFailure(title, MyMessageBundle.message("lsp.devenv.executableNotFound", DevenvCli.EXECUTABLE));
            return;
        }
        if (root == null) {
            return;
        }

        new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                // No timeout: 'devenv up' may have to build the whole environment first.
                ProcessOutput output = execute(DevenvCli.commandLine(executable, root.getPath(), arguments), 0);
                if (output != null && output.getExitCode() != 0) {
                    notifyFailure(title, DevenvCli.stripAnsi(output.getStderr()).strip());
                }
                refreshAsync();
            }
        }.queue();
    }

    private @Nullable ProcessOutput execute(@NotNull GeneralCommandLine commandLine, int timeoutMillis) {
        try {
            return timeoutMillis > 0 ? DevenvCli.run(commandLine, timeoutMillis) : DevenvCli.run(commandLine);
        } catch (ExecutionException e) {
            LOG.warn("Failed to run '" + commandLine.getCommandLineString() + "'", e);
            return null;
        }
    }

    private void notifyFailure(@NotNull String title, @NotNull String details) {
        if (project.isDisposed()) {
            return;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Devenv")
                .createNotification(title, details, NotificationType.ERROR)
                .notify(project);
    }

    private void publish(@NotNull List<DevenvProcess> processes) {
        if (project.isDisposed() || processes.equals(snapshot)) {
            return;
        }
        snapshot = processes;
        project.getMessageBus()
                .syncPublisher(ServiceEventListener.TOPIC)
                .handle(ServiceEventListener.ServiceEvent.createResetEvent(DevenvServiceViewContributor.class));
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> currentPoller = poller;
        if (currentPoller != null) {
            currentPoller.cancel(false);
        }
        descriptors.clear();
    }
}
