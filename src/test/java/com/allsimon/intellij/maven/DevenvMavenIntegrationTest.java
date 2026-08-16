package com.allsimon.intellij.maven;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Pins the {@code devenv eval} contract {@link DevenvMavenHome} depends on, against the real devenv
 * of this checkout. This project builds with Gradle, so Maven is read twice: as it stands, to show
 * that a project which doesn't enable it is left alone, and with {@code -O} forcing the option on,
 * which is the only way to pin the enabled shape without a second fixture project.
 * <p>
 * The layout of the distribution is only checked when the store path happens to have been built -
 * evaluating a package does not fetch it, and no part of this plugin needs it fetched.
 * <p>
 * Skips unless devenv is installed and 'devenv shell' has built this project's environment.
 */
public class DevenvMavenIntegrationTest {
    private static final int TIMEOUT_SECONDS = 120;
    /** Forces the option on for the length of one evaluation. */
    private static final List<String> ENABLE_MAVEN = List.of("-O", DevenvMaven.ENABLE_ATTRIBUTE + ":bool", "true");

    @Test
    public void leavesAloneAProjectThatDoesNotEnableMaven() throws Exception {
        Path root = devenvRoot();

        String output = eval(root, List.of(), DevenvMaven.ENABLE_ATTRIBUTE, DevenvMaven.PACKAGE_ATTRIBUTE);

        assertTrue("the package attribute evaluates even here, which is what makes the flag necessary",
                output.contains(DevenvMaven.PACKAGE_ATTRIBUTE));
        assertNull("this project builds with Gradle", DevenvMaven.parsePackage(output));
    }

    @Test
    public void evaluatesAMavenPackageWhenTheProjectEnablesIt() throws Exception {
        Path root = devenvRoot();

        String storePath = DevenvMaven.parsePackage(
                eval(root, ENABLE_MAVEN, DevenvMaven.ENABLE_ATTRIBUTE, DevenvMaven.PACKAGE_ATTRIBUTE));

        assertNotNull("with the option forced on, devenv has to name a Maven", storePath);
        assertTrue("expected a Maven store path, got " + storePath, storePath.contains("maven"));
    }

    @Test
    public void findsTheHomeInsideTheStorePathOnceItIsBuilt() throws Exception {
        Path root = devenvRoot();

        String storePath = DevenvMaven.parsePackage(
                eval(root, ENABLE_MAVEN, DevenvMaven.ENABLE_ATTRIBUTE, DevenvMaven.PACKAGE_ATTRIBUTE));
        assertNotNull(storePath);
        assumeTrue("the Maven devenv names has never been built here", Files.isDirectory(Path.of(storePath)));

        Path home = DevenvMaven.findHome(Path.of(storePath));
        assertNotNull("no Maven distribution under " + storePath, home);
        assertTrue("a Maven home is the directory holding bin/m2.conf, " + home + " does not",
                Files.isRegularFile(home.resolve("bin/m2.conf")));
    }

    private static String eval(Path root, List<String> options, String... attributes) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("devenv");
        command.addAll(options);
        command.add("eval");
        command.addAll(List.of(attributes));

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
