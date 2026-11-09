package com.allsimon.intellij.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

final class NixRunConfigurationEditor extends SettingsEditor<NixRunConfiguration> {
    private final RawCommandLineEditor arguments = new RawCommandLineEditor();
    private final TextFieldWithBrowseButton directory = new TextFieldWithBrowseButton();

    NixRunConfigurationEditor(Project project) {
        directory.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    }

    @Override
    protected void resetEditorFrom(@NotNull NixRunConfiguration configuration) {
        arguments.setText(configuration.getArguments());
        directory.setText(configuration.getWorkingDirectory());
    }

    @Override
    protected void applyEditorTo(@NotNull NixRunConfiguration configuration) {
        configuration.setArguments(arguments.getText());
        configuration.setWorkingDirectory(directory.getText());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Arguments:", arguments)
                .addLabeledComponent("Working directory (devenv root when empty):", directory)
                .getPanel();
    }
}
