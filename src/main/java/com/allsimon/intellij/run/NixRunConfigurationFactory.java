package com.allsimon.intellij.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class NixRunConfigurationFactory extends ConfigurationFactory {
    private final NixRunConfiguration.Tool tool;

    NixRunConfigurationFactory(@NotNull ConfigurationType type, @NotNull NixRunConfiguration.Tool tool) {
        super(type);
        this.tool = tool;
    }

    @Override
    public @NotNull String getId() {
        return tool.executable();
    }

    @Override
    public @NotNull String getName() {
        return tool.executable();
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new NixRunConfiguration(project, this, tool);
    }

    @Override
    public Class<? extends BaseState> getOptionsClass() {
        return NixRunConfigurationOptions.class;
    }
}
