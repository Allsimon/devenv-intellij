package com.allsimon.intellij.core;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Told when features are switched on or off, so that a feature can take effect - or stop taking
 * effect - without the user restarting the IDE or reopening a project.
 * <p>
 * Application-level, like {@link DevenvSettings} itself: a listener that has per-project work to do
 * iterates over the open projects. Each feature module registers its own listener in plugin.xml
 * rather than the settings knowing about any of them, which is what keeps 'core' free of dependencies
 * on the modules that depend on it.
 */
public interface DevenvSettingsListener {
    @Topic.AppLevel
    Topic<DevenvSettingsListener> TOPIC =
            new Topic<>(DevenvSettingsListener.class, Topic.BroadcastDirection.NONE, true);

    /**
     * @param changed the features whose state actually changed, so a listener can ignore everything
     *                that isn't its own - ticking the Maven box is no reason to restart a language
     *                server.
     */
    void settingsChanged(@NotNull Set<DevenvFeature> changed);
}
