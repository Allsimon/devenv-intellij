package com.allsimon.intellij.gradledist;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Pins the {@code devenv eval} contract {@link DevenvGradleDistribution} depends on, against the real
 * devenv of this checkout - whose devenv.nix enables {@code languages.java.gradle}:
 * <ul>
 *   <li>the two attributes come back as one JSON object {@link DevenvGradle#parsePackage} can read;</li>
 *   <li>the store path they name holds a Gradle distribution that the IDE accepts as a local one,
 *       which means a {@code lib/gradle-launcher-<version>.jar} - and it is not at the top of the
 *       store path, which is the whole reason {@link DevenvGradle#findHome} looks around.</li>
 * </ul>
 * Skips unless devenv is installed and 'devenv shell' has built this project's environment.
 */
public class DevenvGradleIntegrationTest {
    private static final int TIMEOUT_SECONDS = 120;

    @Test
    public void evaluatesAGradleDistributionTheIdeCanUse() throws Exception {
        Path root = devenvRoot();

        String storePath = DevenvGradle.parsePackage(
                eval(root, DevenvGradle.ENABLE_ATTRIBUTE, DevenvGradle.PACKAGE_ATTRIBUTE));

        assertNotNull("this project's devenv.nix enables languages.java.gradle", storePath);
        Path home = DevenvGradle.findHome(Path.of(storePath));
        assertNotNull("no Gradle distribution under " + storePath, home);
        assertTrue("a Gradle home is the directory holding lib/gradle-launcher-*.jar, " + home + " does not",
                hasLauncherJar(home));
    }

    @Test
    public void evaluatesTheGradleTheDevenvShellItselfRuns() throws Exception {
        Path root = devenvRoot();

        String storePath = DevenvGradle.parsePackage(
                eval(root, DevenvGradle.ENABLE_ATTRIBUTE, DevenvGradle.PACKAGE_ATTRIBUTE));
        assertNotNull(storePath);

        assertTrue("the package devenv reports is the one whose 'gradle' a shell would run",
                Files.isExecutable(Path.of(storePath).resolve("bin/gradle")));
    }

    @Test
    public void reportsTheVersionOfTheDistributionItFound() throws Exception {
        Path root = devenvRoot();

        String storePath = DevenvGradle.parsePackage(
                eval(root, DevenvGradle.ENABLE_ATTRIBUTE, DevenvGradle.PACKAGE_ATTRIBUTE));
        assertNotNull(storePath);
        Path home = DevenvGradle.findHome(Path.of(storePath));
        assertNotNull(home);

        // The store path is named after the version, and so is the launcher jar the IDE reads the
        // version from; a mismatch would mean findHome() walked into another distribution entirely.
        String version = storePath.substring(storePath.lastIndexOf('-') + 1);
        assertTrue("expected a gradle-launcher-" + version + ".jar under " + home,
                Files.exists(home.resolve("lib/gradle-launcher-" + version + ".jar")));
    }

    private static boolean hasLauncherJar(Path home) throws Exception {
        try (Stream<Path> jars = Files.list(home.resolve("lib"))) {
            return jars.anyMatch(jar -> jar.getFileName().toString().startsWith("gradle-launcher-"));
        }
    }

    private static String eval(Path root, String... attributes) throws Exception {
        String[] command = new String[attributes.length + 2];
        command[0] = "devenv";
        command[1] = "eval";
        System.arraycopy(attributes, 0, command, 2, attributes.length);

        Process process = new ProcessBuilder(command).directory(root.toFile()).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue("'devenv eval' should answer within " + TIMEOUT_SECONDS + "s",
                process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("'devenv eval' failed: " + stderr, 0, process.exitValue());
        return stdout;
    }

    /**
     * The checkout this test runs from, found by walking up: Gradle runs each module's tests in the
     * module directory, and the devenv.nix used as the fixture sits one level above.
     */
    private static Path devenvRoot() throws Exception {
        assumeTrue("devenv must be installed", isDevenvInstalled());

        Path directory = new File(".").getCanonicalFile().toPath();
        while (directory != null && !Files.isRegularFile(directory.resolve("devenv.nix"))) {
            directory = directory.getParent();
        }
        assumeTrue("this test must run from the devenv-intellij checkout (its own devenv.nix is the fixture)",
                directory != null);
        assumeTrue("'devenv shell' must have built the environment of this project",
                Files.exists(directory.resolve(".devenv/profile")));
        return directory;
    }

    private static boolean isDevenvInstalled() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String entry : path.split(File.pathSeparator)) {
            if (Files.isExecutable(Path.of(entry).resolve("devenv"))) {
                return true;
            }
        }
        return false;
    }
}
