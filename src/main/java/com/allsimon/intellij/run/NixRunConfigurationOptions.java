package com.allsimon.intellij.run;

import com.intellij.execution.configurations.LocatableRunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;

public final class NixRunConfigurationOptions extends LocatableRunConfigurationOptions {
    private final StoredProperty<String> arguments = string("").provideDelegate(this, "arguments");
    private final StoredProperty<String> workingDirectory = string("").provideDelegate(this, "workingDirectory");

    String getArguments() {
        return arguments.getValue(this);
    }

    void setArguments(String value) {
        arguments.setValue(this, value);
    }

    String getWorkingDirectory() {
        return workingDirectory.getValue(this);
    }

    void setWorkingDirectory(String value) {
        workingDirectory.setValue(this, value);
    }
}
