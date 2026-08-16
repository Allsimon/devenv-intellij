package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * The 'devenv' root node: what the whole process manager can be told to do, i.e. the tool window
 * equivalents of {@code devenv up -d} and {@code devenv down}.
 */
final class DevenvRootServiceViewDescriptor extends SimpleServiceViewDescriptor {
    private final DevenvProcessManager manager;

    DevenvRootServiceViewDescriptor(@NotNull DevenvProcessManager manager) {
        super(MyMessageBundle.message("services.devenv.root"), AllIcons.Nodes.Services);
        this.manager = manager;
    }

    @Override
    public @NotNull ActionGroup getToolbarActions() {
        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(DumbAwareAction.create(
                MyMessageBundle.message("services.devenv.up"), AllIcons.Actions.Execute,
                event -> manager.runCommand(MyMessageBundle.message("services.devenv.up"), "up", "-d")));
        actions.add(DumbAwareAction.create(
                MyMessageBundle.message("services.devenv.down"), AllIcons.Actions.Suspend,
                event -> manager.runCommand(MyMessageBundle.message("services.devenv.down"), "down")));
        // Reload rather than refresh: the root action is the user's way of saying "re-read devenv.nix",
        // which is the only thing that picks up a newly declared process.
        actions.add(DumbAwareAction.create(
                MyMessageBundle.message("services.devenv.refresh"), AllIcons.Actions.Refresh,
                event -> manager.reload()));
        return actions;
    }

    @Override
    public @NotNull ActionGroup getPopupActions() {
        return getToolbarActions();
    }

    @Override
    public @Nullable JComponent getContentComponent() {
        JBPanel<?> panel = new JBPanel<>();
        panel.add(new JBLabel(MyMessageBundle.message("services.devenv.rootDescription")));
        return panel;
    }
}
