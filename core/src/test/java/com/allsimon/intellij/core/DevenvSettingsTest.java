package com.allsimon.intellij.core;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DevenvSettingsTest {

    @Test
    public void everyFeatureIsEnabledByDefault() {
        DevenvSettings settings = new DevenvSettings();

        for (DevenvFeature feature : DevenvFeature.values()) {
            assertTrue(feature.name(), settings.isEnabled(feature));
        }
        assertEquals(Set.of(), settings.getState().disabledFeatures);
    }

    @Test
    public void persistsOnlyTheDisabledFeatures() {
        DevenvSettings settings = new DevenvSettings();
        DevenvSettings.State state = new DevenvSettings.State();
        state.disabledFeatures.add(DevenvFeature.TREEFMT.name());

        settings.loadState(state);

        assertFalse(settings.isEnabled(DevenvFeature.TREEFMT));
        assertTrue(settings.isEnabled(DevenvFeature.LSP));
        assertEquals(Set.of(DevenvFeature.TREEFMT.name()), settings.getState().disabledFeatures);
        assertEquals(EnumSet.complementOf(EnumSet.of(DevenvFeature.TREEFMT)), settings.getEnabled());
    }

    @Test
    public void ignoresAFeatureItNoLongerHas() {
        DevenvSettings settings = new DevenvSettings();
        DevenvSettings.State state = new DevenvSettings.State();
        state.disabledFeatures.add("SOMETHING_ELSE");
        state.disabledFeatures.add(DevenvFeature.LSP.name());

        settings.loadState(state);

        assertFalse(settings.isEnabled(DevenvFeature.LSP));
        assertEquals(Set.of(DevenvFeature.LSP.name()), settings.getState().disabledFeatures);
    }

    @Test
    public void loadingAStateReplacesTheOneBefore() {
        DevenvSettings settings = new DevenvSettings();
        DevenvSettings.State first = new DevenvSettings.State();
        first.disabledFeatures.add(DevenvFeature.MAVEN.name());
        settings.loadState(first);

        settings.loadState(new DevenvSettings.State());

        assertTrue(settings.isEnabled(DevenvFeature.MAVEN));
    }

    @Test
    public void reportsTheFeaturesSwitchedInEitherDirection() {
        Set<DevenvFeature> before = EnumSet.of(DevenvFeature.LSP, DevenvFeature.MAVEN);
        Set<DevenvFeature> after = EnumSet.of(DevenvFeature.MAVEN, DevenvFeature.JDK);

        assertEquals(EnumSet.of(DevenvFeature.LSP, DevenvFeature.JDK),
                DevenvSettings.changedBetween(before, after));
    }

    @Test
    public void reportsNothingWhenTheSameFeaturesStayDisabled() {
        Set<DevenvFeature> disabled = EnumSet.of(DevenvFeature.GRADLE);

        assertEquals(Set.of(), DevenvSettings.changedBetween(disabled, EnumSet.copyOf(disabled)));
        assertEquals(Set.of(), DevenvSettings.changedBetween(Set.of(), Set.of()));
    }
}
