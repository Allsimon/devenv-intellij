package com.allsimon.intellij.javascript;

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
 * Pins the {@code devenv eval} contract {@link DevenvNodeInterpreter} depends on, against the real
 * devenv of this checkout. This project has no JavaScript in it, so the option is read twice: as it
 * stands, to show that a project which doesn't enable it is left alone, and with {@code -O} forcing
 * it on, which is the only way to pin the enabled shape without a second fixture project.
 * <p>
 * The layout of the package is only checked when the store path happens to have been built -
 * evaluating a package does not fetch it, and no part of this plugin needs it fetched.
 * <p>
 * Skips unless devenv is installed and 'devenv shell' has built this project's environment.
 */
public class DevenvJavascriptIntegrationTest {
    private static final int TIMEOUT_SECONDS = 120;
    /** Forces the option on for the length of one evaluation. */
    private static final List<String> ENABLE_JAVASCRIPT =
            List.of("-O", DevenvJavascript.ENABLE_ATTRIBUTE + ":bool", "true");

    @Test
    public void leavesAloneAProjectThatDoesNotEnableJavascript() throws Exception {
        Path root = devenvRoot();

        String output = eval(root, List.of(), DevenvJavascript.ENABLE_ATTRIBUTE, DevenvJavascript.PACKAGE_ATTRIBUTE);

        assertTrue("the package attribute evaluates even here, which is what makes the flag necessary",
                output.contains(DevenvJavascript.PACKAGE_ATTRIBUTE));
        assertNull("this project has no JavaScript in it", DevenvJavascript.parsePackage(output));
    }

    @Test
    public void evaluatesANodeJsPackageWhenTheProjectEnablesIt() throws Exception {
        Path root = devenvRoot();

        String storePath = DevenvJavascript.parsePackage(eval(root, ENABLE_JAVASCRIPT,
                DevenvJavascript.ENABLE_ATTRIBUTE, DevenvJavascript.PACKAGE_ATTRIBUTE));

        assertNotNull("with the option forced on, devenv has to name a Node.js", storePath);
        assertTrue("expected a Node.js store path, got " + storePath, storePath.contains("nodejs"));
    }

    @Test
    public void findsTheInterpreterInsideTheStorePathOnceItIsBuilt() throws Exception {
        Path root = devenvRoot();

        String storePath = DevenvJavascript.parsePackage(eval(root, ENABLE_JAVASCRIPT,
                DevenvJavascript.ENABLE_ATTRIBUTE, DevenvJavascript.PACKAGE_ATTRIBUTE));
        assertNotNull(storePath);
        assumeTrue("the Node.js devenv names has never been built here", Files.isDirectory(Path.of(storePath)));

        Path interpreter = DevenvJavascript.findInterpreter(Path.of(storePath));
        assertNotNull("no runnable interpreter under " + storePath, interpreter);
        assertEquals("the IDE is handed the interpreter itself, not the store path",
                Path.of(storePath).resolve("bin/node"), interpreter);
    }

    @Test
    public void leavesAloneAProjectThatEnablesNoPackageManager() throws Exception {
        Path root = devenvRoot();

        String output = eval(root, List.of(), packageManagerAttributes());

        assertTrue("every package attribute evaluates even here, which is what makes the flags necessary",
                output.contains(DevenvPackageManager.PNPM.packageAttribute()));
        assertNull("this project has no JavaScript in it", DevenvJavascript.parsePackageManager(output));
    }

    /**
     * Each of them in turn, forced on by itself: the attribute names have to be the ones devenv
     * knows, and the store path it hands back has to be the tool it was asked for and not another.
     */
    @Test
    public void evaluatesEveryPackageManagerDevenvCanDeclare() throws Exception {
        Path root = devenvRoot();

        for (DevenvPackageManager manager : DevenvPackageManager.values()) {
            List<String> enable = List.of("-O", manager.enableAttribute() + ":bool", "true");

            DevenvJavascript.Declared declared =
                    DevenvJavascript.parsePackageManager(eval(root, enable, packageManagerAttributes()));

            assertNotNull("with " + manager.enableAttribute() + " forced on, devenv has to name a package",
                    declared);
            assertEquals(manager, declared.manager());
            assertTrue("expected a " + manager.packageName() + " store path, got " + declared.storePath(),
                    declared.storePath().contains(manager.packageName()));
        }
    }

    /**
     * The layout matters as much as the path: the IDE runs a package manager out of the directory
     * holding its package.json, and reads which tool it is off that directory's name.
     */
    @Test
    public void findsThePackageInsideTheStorePathOnceItIsBuilt() throws Exception {
        Path root = devenvRoot();

        for (DevenvPackageManager manager : DevenvPackageManager.values()) {
            List<String> enable = List.of("-O", manager.enableAttribute() + ":bool", "true");
            DevenvJavascript.Declared declared =
                    DevenvJavascript.parsePackageManager(eval(root, enable, packageManagerAttributes()));
            assertNotNull(declared);
            Path storePath = Path.of(declared.storePath());
            if (!Files.isDirectory(storePath)) {
                // Evaluating a package does not fetch it, and no part of this plugin needs it fetched.
                continue;
            }

            Path found = manager.findPackage(storePath);
            assertNotNull("no " + manager.packageName() + " the IDE can run under " + storePath, found);
            assertEquals("the IDE reads the manager's name off the last segment of the path",
                    manager.packageName(), found.getFileName().toString());
        }
    }

    private static String[] packageManagerAttributes() {
        List<String> attributes = new ArrayList<>();
        for (DevenvPackageManager manager : DevenvPackageManager.values()) {
            attributes.add(manager.enableAttribute());
            attributes.add(manager.packageAttribute());
        }
        return attributes.toArray(String[]::new);
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
