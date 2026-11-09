package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiManager;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class NixInjectionConfigurable implements Configurable {
    private final JBTextField attributes = new JBTextField();
    private final JBTextField builders = new JBTextField();

    @Override
    public @Nls String getDisplayName() {
        return "Nix";
    }

    @Override
    public @Nullable JComponent createComponent() {
        attributes.getEmptyText().setText(NixInjectionSettings.DEFAULT_SHELL_ATTRIBUTES);
        builders.getEmptyText().setText(NixInjectionSettings.DEFAULT_SHELL_BUILDERS);
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Bash in the attributes matching:", attributes)
                .addLabeledComponent("Bash in the last argument of:", builders)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        NixInjectionSettings settings = NixInjectionSettings.getInstance();
        return !attributes.getText().equals(settings.shellAttributes) || !builders.getText().equals(settings.shellBuilders);
    }

    @Override
    public void apply() throws ConfigurationException {
        try {
            Pattern.compile(attributes.getText());
        } catch (PatternSyntaxException e) {
            throw new ConfigurationException(e.getDescription(), "Invalid Attribute Pattern");
        }
        NixInjectionSettings settings = NixInjectionSettings.getInstance();
        settings.shellAttributes = attributes.getText();
        settings.shellBuilders = builders.getText();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            PsiManager.getInstance(project).dropPsiCaches();
            DaemonCodeAnalyzer.getInstance(project).restart();
        }
    }

    @Override
    public void reset() {
        NixInjectionSettings settings = NixInjectionSettings.getInstance();
        attributes.setText(settings.shellAttributes);
        builders.setText(settings.shellBuilders);
    }
}
