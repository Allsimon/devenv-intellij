package com.allsimon.intellij.maven;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DevenvMavenTest {
    /** What 'devenv eval languages.java.maven.enable languages.java.maven.package' prints. */
    private static final String ENABLED = """
            {
              "languages.java.maven.enable": true,
              "languages.java.maven.package": "/nix/store/xxx-maven-3.9.16"
            }
            """;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsThePackageOfAProjectThatEnablesMaven() {
        assertEquals("/nix/store/xxx-maven-3.9.16", DevenvMaven.parsePackage(ENABLED));
    }

    /**
     * The package option has a default, so it evaluates to a perfectly good store path even in a
     * project that builds with Gradle - which is why the enable flag is evaluated with it.
     */
    @Test
    public void ignoresThePackageOfAProjectThatDoesNotEnableMaven() {
        assertNull(DevenvMaven.parsePackage("""
                {
                  "languages.java.maven.enable": false,
                  "languages.java.maven.package": "/nix/store/xxx-maven-3.9.16"
                }
                """));
    }

    @Test
    public void returnsNothingWhenAnAttributeIsMissingOrMalformed() {
        assertNull(DevenvMaven.parsePackage("{\"languages.java.maven.enable\": true}"));
        assertNull(DevenvMaven.parsePackage("{\"languages.java.maven.package\": \"/nix/store/xxx-maven-3.9.16\"}"));
        assertNull(DevenvMaven.parsePackage("""
                {
                  "languages.java.maven.enable": "true",
                  "languages.java.maven.package": "/nix/store/xxx-maven-3.9.16"
                }
                """));
        assertNull(DevenvMaven.parsePackage("not json at all"));
    }

    /** How the nixpkgs Maven is laid out: a wrapper in bin, the distribution copied into maven/. */
    @Test
    public void findsTheHomeOfANixpkgsStylePackage() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("bin"));
        Files.createFile(storePath.resolve("bin/mvn"));
        Files.createDirectories(storePath.resolve("maven/bin"));
        Files.createFile(storePath.resolve("maven/bin/m2.conf"));

        assertEquals(storePath.resolve("maven"), DevenvMaven.findHome(storePath));
    }

    /** And how a distribution unpacked from apache.org is: bin/m2.conf right there. */
    @Test
    public void findsTheHomeOfAFlatDistribution() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("bin"));
        Files.createFile(storePath.resolve("bin/m2.conf"));

        assertEquals(storePath, DevenvMaven.findHome(storePath));
    }

    @Test
    public void findsNoHomeWithoutTheMarkerTheIdeLooksFor() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("bin"));
        // A wrapper script and nothing else is exactly what the top of the nixpkgs store path holds.
        Files.createFile(storePath.resolve("bin/mvn"));

        assertNull(DevenvMaven.findHome(storePath));
        assertNull("an unbuilt store path is simply not there", DevenvMaven.findHome(storePath.resolve("missing")));
    }
}
