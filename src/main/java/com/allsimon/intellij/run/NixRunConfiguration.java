package com.allsimon.intellij.run;

import com.allsimon.intellij.core.DevenvCli;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** {@code devenv <arguments>} or {@code nix <arguments>}, run in the devenv root unless told otherwise. */
public final class NixRunConfiguration extends LocatableConfigurationBase<NixRunConfigurationOptions> {
    public enum Tool {
        DEVENV,
        NIX;

        public String executable() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable File find() {
            return this == DEVENV ? DevenvCli.findExecutable() : PathEnvironmentVariableUtil.findInPath(executable());
        }
    }

    private final Tool tool;

    NixRunConfiguration(@NotNull Project project, @NotNull NixRunConfigurationFactory factory, @NotNull Tool tool) {
        super(project, factory, tool.executable());
        this.tool = tool;
    }

    public Tool getTool() {
        return tool;
    }

    @Override
    protected @NotNull NixRunConfigurationOptions getOptions() {
        return (NixRunConfigurationOptions) super.getOptions();
    }

    public String getArguments() {
        return getOptions().getArguments();
    }

    public void setArguments(String arguments) {
        getOptions().setArguments(arguments);
    }

    public String getWorkingDirectory() {
        return getOptions().getWorkingDirectory();
    }

    public void setWorkingDirectory(String directory) {
        getOptions().setWorkingDirectory(directory);
    }

    @Override
    public @Nullable String suggestedName() {
        return (tool.executable() + " " + getArguments()).strip();
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new NixRunConfigurationEditor(getProject());
    }

    /** The directory to run in: the one set, else the project's devenv root, else the project's. */
    String directory() {
        if (getWorkingDirectory() != null && !getWorkingDirectory().isBlank()) {
            return getWorkingDirectory();
        }
        VirtualFile root = DevenvCli.findDevenvRoot(getProject());
        return root != null ? root.getPath() : getProject().getBasePath();
    }

    GeneralCommandLine commandLine() throws ExecutionException {
        File executable = tool.find();
        if (executable == null) {
            throw new ExecutionException("'" + tool.executable() + "' is not on the PATH");
        }
        List<String> arguments = ParametersListUtil.parse(getArguments() == null ? "" : getArguments(), false, true);
        return DevenvCli.commandLine(executable, directory(), arguments.toArray(String[]::new));
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
        return new CommandLineState(environment) {
            @Override
            protected @NotNull ProcessHandler startProcess() throws ExecutionException {
                ProcessHandler handler = new KillableColoredProcessHandler(commandLine());
                ProcessTerminatedListener.attach(handler);
                return handler;
            }
        };
    }
}
