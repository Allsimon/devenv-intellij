package com.allsimon.intellij.jdk;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Pins the {@code devenv eval} contract {@link DevenvProjectSdk} depends on, against the real devenv
 * of this checkout - whose devenv.nix enables {@code languages.java}:
 * <ul>
 *   <li>the two attributes come back as one JSON object {@link DevenvJdk#parseHome} can read;</li>
 *   <li>{@code languages.java.jdk.package.home} is a <b>JDK home</b> - it has the {@code release}
 *       file the IDE looks for, which the store path of the package itself does not, and getting
 *       that wrong would make the IDE reject every SDK this plugin sets;</li>
 *   <li>it is the very JDK the devenv shell puts in {@code JAVA_HOME}, so the IDE and a terminal
 *       agree on the toolchain.</li>
 * </ul>
 * Skips unless devenv is installed and 'devenv shell' has built this project's environment.
 */
public class DevenvJdkIntegrationTest {
    private static final int TIMEOUT_SECONDS = 120;
    /** What the devenv shell exports; the JDK home the plugin reads has to be this one. */
    private static final String JAVA_HOME_ATTRIBUTE = "env.JAVA_HOME";

    @Test
    public void evaluatesAUsableJdkHome() throws Exception {
        Path root = devenvRoot();

        String home = DevenvJdk.parseHome(eval(root, DevenvJdk.ENABLE_ATTRIBUTE, DevenvJdk.HOME_ATTRIBUTE));

        assertNotNull("this project's devenv.nix enables languages.java", home);
        Path jdk = Path.of(home);
        assertTrue("a JDK home has to hold bin/java, " + home + " does not", Files.isExecutable(jdk.resolve("bin/java")));
        assertTrue("the IDE identifies a JDK home by its 'release' file, " + home + " has none",
                Files.isRegularFile(jdk.resolve("release")));
    }

    @Test
    public void evaluatesTheJdkTheDevenvShellItselfUses() throws Exception {
        Path root = devenvRoot();

        String home = DevenvJdk.parseHome(eval(root, DevenvJdk.ENABLE_ATTRIBUTE, DevenvJdk.HOME_ATTRIBUTE));
        String javaHome = JsonParser.parseString(eval(root, JAVA_HOME_ATTRIBUTE))
                .getAsJsonObject().get(JAVA_HOME_ATTRIBUTE).getAsString();

        assertEquals("the Project SDK must be the JDK a terminal in this project gets", javaHome, home);
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
