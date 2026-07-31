package com.allsimon.intellij;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
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
import java.util.List;
import java.util.function.Predicate;

/**
 * One process node: its status, the commands that act on it, and a console holding the tail of its
 * log.
 * <p>
 * Instances are cached per process name by {@link DevenvProcessManager}, so {@link #process} is the
 * mutable part - it is replaced on every refresh while the console and its scroll position survive.
 */
final class DevenvProcessServiceViewDescriptor implements ServiceViewDescriptor {
    private static final int LOG_LINES = 200;

    private final DevenvProcessManager manager;
    private final String name;

    private volatile DevenvProcess process;
    /** Created on the EDT by {@link #getContentComponent()}, read by the background log fetch. */
    private volatile ConsoleView console;

    DevenvProcessServiceViewDescriptor(@NotNull DevenvProcessManager manager, @NotNull String name) {
        this.manager = manager;
        this.name = name;
        this.process = DevenvProcess.declared(name, null);
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
        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(new ProcessAction(MyMessageBundle.message("services.devenv.start"), AllIcons.Actions.Execute,
                status -> !status.isAlive(), "processes", "start", name));
        actions.add(new ProcessAction(MyMessageBundle.message("services.devenv.stop"), AllIcons.Actions.Suspend,
                DevenvProcess.Status::isAlive, "processes", "stop", name));
        actions.add(new ProcessAction(MyMessageBundle.message("services.devenv.restart"), AllIcons.Actions.Restart,
                DevenvProcess.Status::isAlive, "processes", "restart", name));
        actions.add(DumbAwareAction.create(
                MyMessageBundle.message("services.devenv.refreshLogs"), AllIcons.Actions.Refresh,
                event -> loadLogs()));
        return actions;
    }

    @Override
    public @NotNull ActionGroup getPopupActions() {
        return getToolbarActions();
    }

    @Override
    public @NotNull JComponent getContentComponent() {
        if (console == null) {
            console = TextConsoleBuilderFactory.getInstance().createBuilder(manager.getProject()).getConsole();
            Disposer.register(manager, console);
        }
        return console.getComponent();
    }

    @Override
    public void onNodeSelected(@Nullable List<Object> selectedServices) {
        loadLogs();
    }

    /**
     * {@code devenv processes logs} is a snapshot - there is no follow mode - so the console is
     * refilled on selection and whenever the user asks for it again.
     */
    private void loadLogs() {
        if (console == null) {
            return;
        }
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            String logs = manager.readLogs(name, LOG_LINES);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (console == null) {
                    return;
                }
                console.clear();
                console.print(logs == null ? MyMessageBundle.message("services.devenv.noLogs") : logs,
                        ConsoleViewContentType.NORMAL_OUTPUT);
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
