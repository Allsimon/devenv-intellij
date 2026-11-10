package com.allsimon.intellij.run;

import com.allsimon.intellij.processes.DevenvServices;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Run icons for devenv.nix, a flake's outputs and inputs, the input's entry in flake.lock, and a Services icon on each
 * declared process.
 */
public final class NixRunLineMarkerContributor extends RunLineMarkerContributor {
    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        if (element.getFirstChild() != null || element.getContainingFile().getVirtualFile() == null) {
            return null;
        }
        VirtualFile file = element.getContainingFile().getVirtualFile();
        Project project = element.getProject();
        List<AnAction> actions = new ArrayList<>();
        for (NixRunTargets.Command command : NixRunTargets.commands(element)) {
            actions.add(new RunCommand(command, file.getParent().getPath()));
        }
        String input = NixRunTargets.input(element);
        VirtualFile lockFile = input == null ? null : file.getParent().findChild("flake.lock");
        FlakeLock.Entry entry = null;
        if (lockFile != null) {
            var document = FileDocumentManager.getInstance().getDocument(lockFile);
            entry = document == null ? null : FlakeLock.find(document.getText(), input);
        }
        if (entry != null) {
            int offset = entry.offset();
            actions.add(DumbAwareAction.create("Open '" + input + "' in flake.lock"
                            + (entry.rev() == null ? "" : " (" + entry.rev().substring(0, Math.min(7, entry.rev().length())) + ")"),
                    e -> new OpenFileDescriptor(project, lockFile, offset).navigate(true)));
        }
        String process = NixRunTargets.process(element);
        if (process != null && DevenvServices.lists(project, file, process)) {
            actions.add(DumbAwareAction.create("Show '" + process + "' in Services",
                    e -> DevenvServices.show(project, file, process)));
        }
        if (actions.isEmpty()) {
            return null;
        }
        return new Info(process != null && actions.size() == 1 ? AllIcons.Toolwindows.ToolWindowServices
                : AllIcons.RunConfigurations.TestState.Run, actions.toArray(AnAction[]::new),
                e -> actions.getFirst().getTemplateText());
    }

    /** Runs a command through a Nix run configuration, reusing the one that runs it already. */
    static final class RunCommand extends DumbAwareAction {
        private final NixRunTargets.Command command;
        private final String directory;

        RunCommand(NixRunTargets.Command command, String directory) {
            super("Run '" + command.label() + "'", null, AllIcons.Actions.Execute);
            this.command = command;
            this.directory = directory;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            Project project = event.getProject();
            if (project != null) {
                ProgramRunnerUtil.executeConfiguration(settings(project), DefaultRunExecutor.getRunExecutorInstance());
            }
        }

        RunnerAndConfigurationSettings settings(Project project) {
            RunManager runManager = RunManager.getInstance(project);
            for (RunnerAndConfigurationSettings existing
                    : runManager.getConfigurationSettingsList(NixRunConfigurationType.getInstance())) {
                if (existing.getConfiguration() instanceof NixRunConfiguration c && c.getTool() == command.tool()
                        && command.arguments().equals(c.getArguments()) && directory.equals(c.getWorkingDirectory())) {
                    runManager.setSelectedConfiguration(existing);
                    return existing;
                }
            }
            RunnerAndConfigurationSettings settings = runManager.createConfiguration(command.label(),
                    NixRunConfigurationType.getInstance().factory(command.tool()));
            NixRunConfiguration configuration = (NixRunConfiguration) settings.getConfiguration();
            configuration.setArguments(command.arguments());
            configuration.setWorkingDirectory(directory);
            settings.setTemporary(true);
            runManager.addConfiguration(settings);
            runManager.setSelectedConfiguration(settings);
            return settings;
        }
    }
}
