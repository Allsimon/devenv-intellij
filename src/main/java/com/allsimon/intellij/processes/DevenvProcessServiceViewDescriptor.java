package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * One process node: its status, the commands that act on it, and a console holding its log.
 * <p>
 * Instances are cached per process name by {@link DevenvProcessManager}, so {@link #process} is the
 * mutable part - it is replaced on every refresh while the console and its scroll position survive.
 */
final class DevenvProcessServiceViewDescriptor implements ServiceViewDescriptor {
    private static final int LOG_LINES = 200;

    private final DevenvProcessManager manager;
    private final String name;
    /**
     * Built once: the toolbar polls {@link #getToolbarActions()} several times a second and reports
     * an error if it is handed new action instances every time. The actions read {@link #process}
     * when they update, so they stay correct as it changes.
     */
    private final ActionGroup actions;

    private volatile DevenvProcess process;
    /** Created on the EDT by {@link #getContentComponent()}, read by the background log fetch. */
    private volatile DevenvLogConsole console;
    /** Non-null once the process manager's log files have been found at least once. */
    private volatile DevenvProcessLogTail tail;

    DevenvProcessServiceViewDescriptor(@NotNull DevenvProcessManager manager, @NotNull String name) {
        this.manager = manager;
        this.name = name;
        this.process = DevenvProcess.declared(name, null);

        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ProcessAction(MyMessageBundle.message("services.devenv.start"), AllIcons.Actions.Execute,
                status -> !status.isAlive(), "processes", "start", name));
        group.add(new ProcessAction(MyMessageBundle.message("services.devenv.stop"), AllIcons.Actions.Suspend,
                DevenvProcess.Status::isAlive, "processes", "stop", name));
        group.add(new ProcessAction(MyMessageBundle.message("services.devenv.restart"), AllIcons.Actions.Restart,
                DevenvProcess.Status::isAlive, "processes", "restart", name));
        group.add(DumbAwareAction.create(
                MyMessageBundle.message("services.devenv.refreshLogs"), AllIcons.Actions.Refresh,
                event -> showLogs()));
        this.actions = group;
    }

    void setProcess(@NotNull DevenvProcess process) {
        this.process = process;
    }

    @Override
    public @NotNull String getId() {
        return name;
    }

    @Override
    public @NotNull String getUniqueId() {
        return name;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        DevenvProcess current = process;
        return new PresentationData(name, statusText(current), icon(current.status()), null);
    }

    private static @NotNull String statusText(@NotNull DevenvProcess process) {
        String status = process.status().displayName();
        Integer restarts = process.restarts();
        return restarts != null && restarts > 0
                ? MyMessageBundle.message("services.devenv.statusWithRestarts", status, restarts)
                : status;
    }

    private static @NotNull Icon icon(DevenvProcess.@NotNull Status status) {
        if (status.isAlive()) {
            return AllIcons.RunConfigurations.TestState.Run;
        }
        // 'exited' covers a one-shot process that finished normally as much as one that crashed - devenv
        // reports no exit code here - so only 'gave up' is worth flagging as an error.
        return status == DevenvProcess.Status.GAVE_UP ? AllIcons.RunConfigurations.TestError : AllIcons.Nodes.EmptyNode;
    }

    @Override
    public @NotNull ActionGroup getToolbarActions() {
        return actions;
    }

    @Override
    public @NotNull ActionGroup getPopupActions() {
        return actions;
    }

    @Override
    public @NotNull JComponent getContentComponent() {
        if (console != null) {
            return console.getComponent();
        }
        console = new DevenvLogConsole(manager.getProject(), name);
        Disposer.register(manager, console);
        JComponent component = console.getComponent();
        // The platform is free to select the node before it builds this pane, and selection is what
        // would otherwise start the follower - showLogs() can do nothing until the console exists. Both
        // orders have to work, or the console sits empty until 'Refresh Logs' is pressed.
        showLogs();
        return component;
    }

    @Override
    public void onNodeSelected(@Nullable List<Object> selectedServices) {
        showLogs();
    }

    @Override
    public void onNodeUnselected() {
        DevenvProcessLogTail current = tail;
        if (current != null) {
            // Nothing is watching the console now, so stop reading into it. Selecting the node again
            // restarts from the tail of the file, which is where the console had got to anyway.
            current.stop();
        }
    }

    /**
     * Follows the process manager's log files where it writes them, and falls back to the
     * {@code devenv processes logs} snapshot where it doesn't - the files are an internal layout of
     * the 'native' process manager, and the command works whichever one is configured.
     */
    private void showLogs() {
        DevenvLogConsole current = console;
        if (current == null) {
            return;
        }
        current.clear();

        Path directory = manager.findLogDirectory();
        if (directory == null) {
            tail = null;
            loadSnapshot(current);
            return;
        }
        DevenvProcessLogTail following = tail;
        if (following == null) {
            following = new DevenvProcessLogTail(current, directory, name);
            Disposer.register(manager, following);
            tail = following;
        }
        // Stop first so the readers reopen: the console was just cleared, and re-priming them fills it
        // back up with the tail of the file. That also makes this the whole of the refresh action.
        following.stop();
        following.start();
    }

    /** One-shot read for the process managers that keep their logs to themselves. */
    private void loadSnapshot(@NotNull DevenvLogConsole target) {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            String logs = manager.readLogs(name, LOG_LINES);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (console != target) {
                    return;
                }
                target.appendText(logs == null ? MyMessageBundle.message("services.devenv.noLogs") : logs, false);
            }, manager.getProject().getDisposed());
        });
    }

    /** A devenv sub-command that only makes sense in some of a process's states. */
    private final class ProcessAction extends DumbAwareAction {
        private final Predicate<DevenvProcess.Status> enabledWhen;
        private final String title;
        private final String[] arguments;

        private ProcessAction(@NotNull String title, @NotNull Icon icon,
                              @NotNull Predicate<DevenvProcess.Status> enabledWhen, String @NotNull ... arguments) {
            super(title, null, icon);
            this.title = title;
            this.enabledWhen = enabledWhen;
            this.arguments = arguments;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            event.getPresentation().setEnabled(enabledWhen.test(process.status()));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            manager.runCommand(title, arguments);
        }
    }
}
