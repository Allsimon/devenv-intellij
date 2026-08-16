package com.allsimon.intellij.gradledist;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DevenvGradleTest {
    /** What 'devenv eval languages.java.gradle.enable languages.java.gradle.package' prints. */
    private static final String ENABLED = """
            {
              "languages.java.gradle.enable": true,
              "languages.java.gradle.package": "/nix/store/xxx-gradle-9.5.1"
            }
            """;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsThePackageOfAProjectThatEnablesGradle() {
        assertEquals("/nix/store/xxx-gradle-9.5.1", DevenvGradle.parsePackage(ENABLED));
    }

    /**
     * The package option has a default, so it evaluates to a perfectly good store path even in a
     * project that builds with something else - which is why the enable flag is evaluated with it.
     */
    @Test
    public void ignoresThePackageOfAProjectThatDoesNotEnableGradle() {
        assertNull(DevenvGradle.parsePackage("""
                {
                  "languages.java.gradle.enable": false,
                  "languages.java.gradle.package": "/nix/store/xxx-gradle-9.5.1"
                }
                """));
    }

    @Test
    public void returnsNothingWhenAnAttributeIsMissingOrMalformed() {
        assertNull(DevenvGradle.parsePackage("{\"languages.java.gradle.enable\": true}"));
        assertNull(DevenvGradle.parsePackage("{\"languages.java.gradle.package\": \"/nix/store/xxx-gradle-9.5.1\"}"));
        assertNull(DevenvGradle.parsePackage("""
                {
                  "languages.java.gradle.enable": "true",
                  "languages.java.gradle.package": "/nix/store/xxx-gradle-9.5.1"
                }
                """));
        assertNull(DevenvGradle.parsePackage("[]"));
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
