package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.util.function.Consumer;

/**
 * The 'Devenv' root node: what the whole process manager can be told to do, i.e. the tool window
 * equivalents of {@code devenv up -d} and {@code devenv down}.
 * <p>
 * The Services tool window only shows it when 'Group Services by Type' is on; with it off the
 * processes are listed at the top level and these actions live on the tool window's own toolbar.
 * Either way there is one instance per project, cached by {@link DevenvProcessManager} - a descriptor
 * handing out fresh toolbar actions on every poll is an error the action system reports.
 */
final class DevenvRootServiceViewDescriptor extends SimpleServiceViewDescriptor {
    private final ActionGroup actions;

    DevenvRootServiceViewDescriptor(@NotNull DevenvProcessManager manager) {
        super(MyMessageBundle.message("services.devenv.root"), AllIcons.Nodes.Services);
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(command(manager, "services.devenv.up", AllIcons.Actions.Execute, "up", "-d"));
        group.add(command(manager, "services.devenv.down", AllIcons.Actions.Suspend, "down"));
        // Reload rather than refresh: this action is the user's way of saying "re-read devenv.nix",
        // which is the only thing that picks up a newly declared process.
        group.add(action(manager, "services.devenv.refresh", AllIcons.Actions.Refresh, DevenvProcessManager::reload));
        this.actions = group;
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
    public @Nullable JComponent getContentComponent() {
        JBPanel<?> panel = new JBPanel<>();
        panel.add(new JBLabel(MyMessageBundle.message("services.devenv.rootDescription")));
        return panel;
    }

    private static @NotNull AnAction command(@NotNull DevenvProcessManager manager, @NotNull String titleKey,
                                             @NotNull Icon icon, String @NotNull ... arguments) {
        String title = MyMessageBundle.message(titleKey);
        return action(manager, titleKey, icon, target -> target.runCommand(title, arguments));
    }

    private static @NotNull AnAction action(@NotNull DevenvProcessManager manager, @NotNull String titleKey,
                                            @NotNull Icon icon, @NotNull Consumer<DevenvProcessManager> body) {
        return DumbAwareAction.create(MyMessageBundle.message(titleKey), icon, event -> body.accept(manager));
    }
}
