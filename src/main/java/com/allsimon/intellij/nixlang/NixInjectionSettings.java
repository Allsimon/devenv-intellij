package com.allsimon.intellij.nixlang;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.affogato.parser.Injections;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/** Which attributes and builders get Bash injected. */
@Service
@State(name = "NixInjection", storages = @Storage("nix.xml"))
public final class NixInjectionSettings implements PersistentStateComponent<NixInjectionSettings> {
    public static final String DEFAULT_SHELL_ATTRIBUTES = Injections.Rules.SHELL_ATTRIBUTES;
    public static final String DEFAULT_SHELL_BUILDERS =
            String.join(", ", new TreeSet<>(Injections.Rules.SHELL_BUILDERS));

    /** A regular expression the whole attribute name must match. */
    public String shellAttributes = DEFAULT_SHELL_ATTRIBUTES;
    /** Comma-separated names of the functions whose last argument is a script. */
    public String shellBuilders = DEFAULT_SHELL_BUILDERS;

    public static NixInjectionSettings getInstance() {
        return ApplicationManager.getApplication().getService(NixInjectionSettings.class);
    }

    /** The attribute pattern, or one matching nothing while it does not compile. */
    Pattern shellAttributePattern() {
        try {
            return Pattern.compile(shellAttributes);
        } catch (PatternSyntaxException e) {
            return Pattern.compile("(?!)");
        }
    }

    Set<String> shellBuilderNames() {
        return Arrays.stream(shellBuilders.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }

    Injections.Rules rules() {
        return new Injections.Rules(shellAttributePattern(), shellBuilderNames());
    }

    @Override
    public NixInjectionSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull NixInjectionSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
