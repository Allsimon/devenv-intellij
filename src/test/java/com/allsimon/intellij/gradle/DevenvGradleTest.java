package com.allsimon.intellij.gradle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DevenvGradleTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsWhetherTheProjectBuildsWithGradle() {
        assertTrue(DevenvGradle.parseEnabled(flags(true, true)));
        assertFalse(DevenvGradle.parseEnabled(flags(true, false)));
    }

    /**
     * The package option only has a value when {@code languages.java} is enabled: evaluating it
     * otherwise fails, so the package is not asked for at all then.
     */
    @Test
    public void readsAProjectWithoutJavaAsNotBuildingWithGradle() {
        assertFalse(DevenvGradle.parseEnabled(flags(false, true)));
    }

    @Test
    public void readsMissingOrMalformedFlagsAsDisabled() {
        assertFalse(DevenvGradle.parseEnabled("{\"languages.java.gradle.enable\": true}"));
        assertFalse(DevenvGradle.parseEnabled("""
                {
                  "languages.java.enable": true,
                  "languages.java.gradle.enable": "true"
                }
                """));
    }

    @Test
    public void readsThePackage() {
        assertEquals("/nix/store/xxx-gradle-9.5.1",
                DevenvGradle.parsePackage("{\"languages.java.gradle.package\": \"/nix/store/xxx-gradle-9.5.1\"}"));
    }

    @Test
    public void returnsNothingWhenThePackageIsMissingOrMalformed() {
        assertNull(DevenvGradle.parsePackage("{\"languages.java.gradle.enable\": true}"));
        assertNull(DevenvGradle.parsePackage("{\"languages.java.gradle.package\": 1}"));
        assertNull(DevenvGradle.parsePackage("[]"));
    }

    /** What 'devenv eval languages.java.enable languages.java.gradle.enable' prints. */
    private static String flags(boolean java, boolean gradle) {
        return "{\"languages.java.enable\": " + java + ", \"languages.java.gradle.enable\": " + gradle + "}";
    }

    /** How the nixpkgs Gradle is laid out: a wrapper in bin, the distribution under libexec/gradle. */
    @Test
    public void findsTheHomeOfANixpkgsStylePackage() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("bin"));
        Files.createFile(storePath.resolve("bin/gradle"));
        Files.createDirectories(storePath.resolve("libexec/gradle/lib"));
        Files.createFile(storePath.resolve("libexec/gradle/lib/gradle-launcher-9.5.1.jar"));

        assertEquals(storePath.resolve("libexec/gradle"), DevenvGradle.findHome(storePath));
    }

    /** And how a distribution unpacked from gradle.org is: the launcher jar right there. */
    @Test
    public void findsTheHomeOfAFlatDistribution() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("lib"));
        Files.createFile(storePath.resolve("lib/gradle-launcher-9.5.1.jar"));

        assertEquals(storePath, DevenvGradle.findHome(storePath));
    }

    @Test
    public void findsNoHomeWithoutALauncherJar() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("lib"));
        Files.createFile(storePath.resolve("lib/gradle-core-9.5.1.jar"));

        assertNull(DevenvGradle.findHome(storePath));
        assertNull("an unbuilt store path is simply not there", DevenvGradle.findHome(storePath.resolve("missing")));
    }
}
