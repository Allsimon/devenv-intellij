package com.allsimon.intellij.maven;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DevenvMavenTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsWhetherTheProjectBuildsWithMaven() {
        assertTrue(DevenvMaven.parseEnabled(flags(true, true)));
        assertFalse(DevenvMaven.parseEnabled(flags(true, false)));
    }

    /**
     * The package option only has a value when {@code languages.java} is enabled: evaluating it
     * otherwise fails, so the package is not asked for at all then.
     */
    @Test
    public void readsAProjectWithoutJavaAsNotBuildingWithMaven() {
        assertFalse(DevenvMaven.parseEnabled(flags(false, true)));
    }

    @Test
    public void readsMissingOrMalformedFlagsAsDisabled() {
        assertFalse(DevenvMaven.parseEnabled("{\"languages.java.maven.enable\": true}"));
        assertFalse(DevenvMaven.parseEnabled("""
                {
                  "languages.java.enable": true,
                  "languages.java.maven.enable": "true"
                }
                """));
    }

    @Test
    public void readsThePackage() {
        assertEquals("/nix/store/xxx-maven-3.9.16",
                DevenvMaven.parsePackage("{\"languages.java.maven.package\": \"/nix/store/xxx-maven-3.9.16\"}"));
    }

    @Test
    public void returnsNothingWhenThePackageIsMissingOrMalformed() {
        assertNull(DevenvMaven.parsePackage("{\"languages.java.maven.enable\": true}"));
        assertNull(DevenvMaven.parsePackage("{\"languages.java.maven.package\": 1}"));
        assertNull(DevenvMaven.parsePackage("not json at all"));
    }

    /** What 'devenv eval languages.java.enable languages.java.maven.enable' prints. */
    private static String flags(boolean java, boolean maven) {
        return "{\"languages.java.enable\": " + java + ", \"languages.java.maven.enable\": " + maven + "}";
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
