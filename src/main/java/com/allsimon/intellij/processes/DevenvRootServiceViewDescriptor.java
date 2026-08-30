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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A node standing for one or more devenv roots: what their process managers can be told to do, i.e.
 * the tool window equivalents of {@code devenv up -d} and {@code devenv down}.
 * <p>
 * It is both the 'Devenv' node, whose commands act on every root of the project, and - in a project
 * holding several devenv.nix - the node of a single root. The Services tool window only shows the
 * 'Devenv' one when 'Group Services by Type' is on; with it off the processes, or the root nodes they
 * hang off, are listed at the top level and these actions live on the tool window's own toolbar.
 * <p>
 * Either way there is one instance per node, cached by its owner - a descriptor handing out fresh
 * toolbar actions on every poll is an error the action system reports. The roots themselves are
 * looked up when an action runs rather than captured, because a project gains and loses them with
 * its devenv.nix files.
 */
final class DevenvRootServiceViewDescriptor extends SimpleServiceViewDescriptor {
    private final String description;
    private final ActionGroup actions;

    /** The node of a single devenv root, named after the directory its devenv.nix is in. */
    static @NotNull DevenvRootServiceViewDescriptor of(@NotNull DevenvProcessManager manager) {
        return new DevenvRootServiceViewDescriptor(
                manager.getRoot().getName(),
                MyMessageBundle.message("services.devenv.rootDescriptionIn", manager.getRoot().getPath()),
                AllIcons.Nodes.Folder,
                () -> List.of(manager));
    }

    /** The 'Devenv' node, standing for every devenv root of the project at once. */
    static @NotNull DevenvRootServiceViewDescriptor of(@NotNull DevenvProcessRoots roots) {
        return new DevenvRootServiceViewDescriptor(
                MyMessageBundle.message("services.devenv.root"),
                MyMessageBundle.message("services.devenv.rootDescription"),
                AllIcons.Nodes.Services,
                roots::managers);
    }

    private DevenvRootServiceViewDescriptor(@NotNull String name, @NotNull String description, @NotNull Icon icon,
                                            @NotNull Supplier<List<DevenvProcessManager>> targets) {
        super(name, icon);
        this.description = description;

        DefaultActionGroup group = new DefaultActionGroup();
        group.add(command(targets, "services.devenv.up", AllIcons.Actions.Execute, "up", "-d"));
        group.add(command(targets, "services.devenv.down", AllIcons.Actions.Suspend, "down"));
        // Reload rather than refresh: this action is the user's way of saying "re-read devenv.nix",
        // which is the only thing that picks up a newly declared process.
        group.add(action(targets, "services.devenv.refresh", AllIcons.Actions.Refresh, DevenvProcessManager::reload));
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
        panel.add(new JBLabel(description));
        return panel;
    }

    private static @NotNull AnAction command(@NotNull Supplier<List<DevenvProcessManager>> targets,
                                             @NotNull String titleKey, @NotNull Icon icon,
                                             String @NotNull ... arguments) {
        String title = MyMessageBundle.message(titleKey);
        return action(targets, titleKey, icon, target -> target.runCommand(title, arguments));
    }

    private static @NotNull AnAction action(@NotNull Supplier<List<DevenvProcessManager>> targets,
                                            @NotNull String titleKey, @NotNull Icon icon,
                                            @NotNull Consumer<DevenvProcessManager> body) {
        return DumbAwareAction.create(MyMessageBundle.message(titleKey), icon,
                event -> targets.get().forEach(body));
    }
}
