package com.allsimon.intellij.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which features of this plugin are switched on, for every project of this IDE.
 * <p>
 * Only the disabled features are persisted, so that everything is on by default, an untouched install
 * writes no file at all, and a feature added later needs no migration to be on for existing users.
 */
@Service
@State(name = "DevenvSettings", storages = @Storage("devenv.xml"), category = SettingsCategory.TOOLS)
public final class DevenvSettings implements PersistentStateComponent<DevenvSettings.State> {
    private static final Logger LOG = Logger.getInstance(DevenvSettings.class);

    private volatile Set<DevenvFeature> disabled = EnumSet.noneOf(DevenvFeature.class);

    public static @NotNull DevenvSettings getInstance() {
        return ApplicationManager.getApplication().getService(DevenvSettings.class);
    }

    public boolean isEnabled(@NotNull DevenvFeature feature) {
        return !disabled.contains(feature);
    }

    public @NotNull Set<DevenvFeature> getEnabled() {
        return EnumSet.complementOf(copyOf(disabled));
    }

    /**
     * Makes exactly {@code enabled} the enabled features and tells the listeners which ones moved.
     * Nothing is published when nothing changed, so applying an untouched settings page is free.
     */
    public void setEnabled(@NotNull Set<DevenvFeature> enabled) {
        Set<DevenvFeature> wanted = EnumSet.complementOf(copyOf(enabled));
        Set<DevenvFeature> changed = changedBetween(disabled, wanted);
        if (changed.isEmpty()) {
            return;
        }

        LOG.info("Devenv features toggled: " + changed + ", now disabled: " + wanted);
        disabled = wanted;
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(DevenvSettingsListener.TOPIC)
                .settingsChanged(changed);
    }

    @Override
    public @NotNull State getState() {
        State state = new State();
        for (DevenvFeature feature : disabled) {
            state.disabledFeatures.add(feature.name());
        }
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        Set<DevenvFeature> loaded = EnumSet.noneOf(DevenvFeature.class);
        for (String name : state.disabledFeatures) {
            try {
                loaded.add(DevenvFeature.valueOf(name));
            } catch (IllegalArgumentException e) {
                // A feature this version no longer has: ignored rather than fatal, so that going back
                // to an older build doesn't leave the settings unreadable.
                LOG.info("Ignoring unknown devenv feature '" + name + "' in the settings");
            }
        }
        disabled = loaded;
    }

    /**
     * The features disabled in exactly one of the two sets - a symmetric difference - which is to say
     * the ones that were switched.
     */
    static @NotNull Set<DevenvFeature> changedBetween(@NotNull Set<DevenvFeature> disabledBefore,
                                                      @NotNull Set<DevenvFeature> disabledAfter) {
        Set<DevenvFeature> changed = copyOf(disabledAfter);
        for (DevenvFeature feature : disabledBefore) {
            if (!changed.add(feature)) {
                changed.remove(feature);
            }
        }
        return changed;
    }

    private static @NotNull EnumSet<DevenvFeature> copyOf(@NotNull Set<DevenvFeature> features) {
        // EnumSet.copyOf refuses an empty plain collection, having no way to guess the enum type
        // from it - and 'everything enabled' hands it exactly that.
        return features.isEmpty() ? EnumSet.noneOf(DevenvFeature.class) : EnumSet.copyOf(features);
    }

    /** Sorted, so that the file on disk doesn't churn on the order features happen to be iterated in. */
    public static final class State {
        public Set<String> disabledFeatures = new TreeSet<>();
    }
}
