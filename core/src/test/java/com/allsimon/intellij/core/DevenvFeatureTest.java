package com.allsimon.intellij.core;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DevenvFeatureTest {

    /**
     * The settings page writes a group's heading when the group of the feature it is about to add
     * differs from the last one, so a group split in two would be headed twice.
     */
    @Test
    public void featuresOfAGroupFollowEachOther() {
        Set<DevenvFeatureGroup> seen = EnumSet.noneOf(DevenvFeatureGroup.class);
        DevenvFeatureGroup previous = null;

        for (DevenvFeature feature : DevenvFeature.values()) {
            if (feature.group() != previous) {
                assertTrue(feature.group() + " is split over the enum", seen.add(feature.group()));
                previous = feature.group();
            }
        }
    }

    @Test
    public void everyFeatureIsNamedAndDescribed() {
        for (DevenvFeature feature : DevenvFeature.values()) {
            assertNotNull(feature.name(), feature.displayName());
            assertFalse(feature.name() + " has no name", feature.displayName().isBlank());
            assertFalse(feature.name() + " has no description", feature.description().isBlank());
        }
    }

    @Test
    public void everyGroupIsNamed() {
        for (DevenvFeatureGroup group : DevenvFeatureGroup.values()) {
            assertFalse(group.name() + " has no title", group.displayName().isBlank());
        }
    }

    /** The features of a plugin this IDE always has cannot be hidden from the settings page. */
    @Test
    public void featuresWithoutARequiredPluginAreAlwaysAvailable() {
        assertTrue(DevenvFeature.LSP.isAvailable());
        assertTrue(DevenvFeature.PROCESSES.isAvailable());
        assertTrue(DevenvFeature.TREEFMT.isAvailable());
        assertTrue(DevenvFeature.EXCLUDE.isAvailable());
    }
}
