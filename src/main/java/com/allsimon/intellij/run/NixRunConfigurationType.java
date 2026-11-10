package com.allsimon.intellij.run;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NotNullLazyValue;

/** Runs {@code devenv} or {@code nix} with the arguments a configuration holds. */
public final class NixRunConfigurationType extends ConfigurationTypeBase {
    public static final String ID = "NixCommand";

    public NixRunConfigurationType() {
        super(ID, "Nix", "Runs a devenv or nix command", NotNullLazyValue.createValue(() -> AllIcons.Actions.Execute));
        addFactory(new NixRunConfigurationFactory(this, NixRunConfiguration.Tool.DEVENV));
        addFactory(new NixRunConfigurationFactory(this, NixRunConfiguration.Tool.NIX));
    }

    public static NixRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(NixRunConfigurationType.class);
    }

    public NixRunConfigurationFactory factory(NixRunConfiguration.Tool tool) {
        return (NixRunConfigurationFactory) getConfigurationFactories()[tool.ordinal()];
    }
}
