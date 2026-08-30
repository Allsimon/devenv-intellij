package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
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
import java.nio.file.Path;
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
 * {@link #getProcessNames()}, never spawn a process.
 */
@Service(Service.Level.PROJECT)
final class DevenvProcessManager implements Disposable {
    private static final Logger LOG = Logger.getInstance(DevenvProcessManager.class);

    private static final long POLL_INTERVAL_SECONDS = 5;
    // 'devenv processes list' only talks to the already-running manager - it answered in ~80ms in
    // every state we measured - so a short timeout here keeps a hung manager from stalling the poll.
    private static final int LIST_TIMEOUT_MILLIS = 10_000;
    private static final int LOGS_TIMEOUT_MILLIS = 30_000;
    /** Where devenv says the process manager keeps its socket and its logs. */
    private static final String RUNTIME_ATTRIBUTE = "devenv.runtime";

    private final Project project;
    private final Map<String, DevenvProcessServiceViewDescriptor> descriptors = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();
    private final AtomicBoolean pollingStarted = new AtomicBoolean();

    private DevenvRootServiceViewDescriptor rootDescriptor;
    private volatile List<DevenvProcess> snapshot = List.of();
    /** Read from the same evaluation as {@link #declared}, and dropped with it on a reload. */
    private volatile Path runtimeDirectory;
    /** Result of the last {@code devenv eval processes}; {@code null} until it has run at least once. */
    private volatile List<DevenvProcess> declared;
    private volatile ScheduledFuture<?> poller;

    DevenvProcessManager(@NotNull Project project) {
        this.project = project;
    }

    static @NotNull DevenvProcessManager getInstance(@NotNull Project project) {
        return project.getService(DevenvProcessManager.class);
    }

    /**
     * The names of the processes to show, as of the last refresh. Never blocks.
     * <p>
     * Names rather than {@link DevenvProcess} values: the Services tree keeps whatever it is given as
     * a node's identity, and only finds that node again for an in-place update if the value still
     * compares equal. A process whose status just changed is a different record, so contributing the
     * records themselves would leave nothing but a full tree reset - and a reset drops the selection.
     */
    @NotNull List<String> getProcessNames() {
        startPolling();
        return snapshot.stream().map(DevenvProcess::name).toList();
    }

    @NotNull Project getProject() {
        return project;
    }

    /** One per project, because the platform asks for it again on every model rebuild. */
    synchronized @NotNull DevenvRootServiceViewDescriptor rootDescriptor() {
        if (rootDescriptor == null) {
            rootDescriptor = new DevenvRootServiceViewDescriptor(this);
        }
        return rootDescriptor;
    }

    /**
     * Descriptors are cached per name so that a tree reset doesn't throw away the console - and the
     * scroll position in it - of a node that is still the same process.
     */
    @NotNull DevenvProcessServiceViewDescriptor descriptorFor(@NotNull String name) {
        return descriptors.computeIfAbsent(name, key -> new DevenvProcessServiceViewDescriptor(this, key));
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
        runtimeDirectory = null;
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

    /**
     * Reads the declared processes and, in the same evaluation, where the process manager keeps its
     * runtime state - {@code devenv eval} takes several attributes at once, and evaluating the
     * configuration twice would cost minutes against a cold cache.
     */
    private @NotNull List<DevenvProcess> evaluateDeclared(@NotNull File executable, @NotNull VirtualFile root) {
        ProcessOutput output = execute(
                DevenvCli.commandLine(executable, root.getPath(), "eval", "processes", RUNTIME_ATTRIBUTE), 0);
        if (output == null) {
            return List.of();
        }
        if (output.getExitCode() != 0) {
            LOG.warn("'devenv eval processes' exited with " + output.getExitCode() + ": " + output.getStderr());
            return List.of();
        }
        try {
            String runtime = DevenvProcessParser.parseRuntimeDirectory(output.getStdout());
            runtimeDirectory = runtime == null ? null : Path.of(runtime);
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

    /**
     * Where the running process manager writes its per-process logs, or {@code null} when it writes
     * none this plugin knows how to find - which sends the caller back to the
     * {@code devenv processes logs} snapshot.
     */
    @Nullable Path findLogDirectory() {
        Path runtime = runtimeDirectory;
        return runtime == null ? null : DevenvProcessLogTail.logDirectory(runtime);
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
            // Only reachable if the project stopped being a devenv project between the node appearing
            // and the click, but a command that quietly does nothing is indistinguishable from a broken
            // one - so say so rather than returning.
            notifyFailure(title, MyMessageBundle.message("services.devenv.rootNotFound"));
            return;
        }

        new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                // No timeout: 'devenv up' may have to build the whole environment first.
                ProcessOutput output = execute(DevenvCli.commandLine(executable, root.getPath(), arguments), 0);
                if (output == null) {
                    // execute() has already logged why; without this the command looks like a no-op.
                    notifyFailure(title, MyMessageBundle.message("services.devenv.commandFailed",
                            String.join(" ", arguments)));
                } else if (output.getExitCode() != 0) {
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
        List<DevenvProcess> previous = snapshot;
        if (project.isDisposed() || processes.equals(previous)) {
            return;
        }
        snapshot = processes;
        // The descriptors are what the view reads, so they have to be right before it is told anything.
        for (DevenvProcess process : processes) {
            descriptorFor(process.name()).setProcess(process);
        }

        ServiceEventListener publisher = project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC);
        if (!names(previous).equals(names(processes))) {
            publisher.handle(ServiceEventListener.ServiceEvent.createResetEvent(DevenvServiceViewContributor.class));
            return;
        }
        // Same processes, different states: update those nodes in place. A reset would rebuild the tree
        // and drop the selection, which is exactly what the user is looking at when they press Start.
        Map<String, DevenvProcess> before = byName(previous);
        for (DevenvProcess process : processes) {
            if (!process.equals(before.get(process.name()))) {
                publisher.handle(ServiceEventListener.ServiceEvent.createEvent(
                        ServiceEventListener.EventType.SERVICE_CHANGED,
                        process.name(),
                        DevenvServiceViewContributor.class));
            }
        }
    }

    private static @NotNull List<String> names(@NotNull List<DevenvProcess> processes) {
        return processes.stream().map(DevenvProcess::name).toList();
    }

    private static @NotNull Map<String, DevenvProcess> byName(@NotNull List<DevenvProcess> processes) {
        Map<String, DevenvProcess> byName = new LinkedHashMap<>();
        for (DevenvProcess process : processes) {
            byName.put(process.name(), process);
        }
        return byName;
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
