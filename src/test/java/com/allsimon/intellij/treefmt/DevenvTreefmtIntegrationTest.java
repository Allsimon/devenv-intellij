package com.allsimon.intellij.treefmt;

import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Pins the {@code treefmt --stdin} contract that {@link DevenvTreefmtFormattingService} depends on,
 * against the real wrapper devenv generated for this checkout:
 * <ul>
 *   <li>the formatted text arrives on <b>stdout</b> (its logs go to stderr, and {@code --quiet}
 *       silences them) - if that ever changed, the plugin would write log lines into the user's
 *       file;</li>
 *   <li>a file no formatter is configured for is passed through <b>unchanged</b> with exit 0.</li>
 * </ul>
 * Skips unless 'devenv shell' has built this project's profile.
 */
public class DevenvTreefmtIntegrationTest {
    private static final int TIMEOUT_SECONDS = 60;

    @Test
    public void formatsNixOnStdout() throws Exception {
        File treefmt = treefmt();
        String unformatted = "{ pkgs, ... }: {\n  languages.java.enable     =    true;\n}\n";

        Result result = run(treefmt, "devenv.nix", unformatted);

        assertEquals("treefmt should succeed on a valid Nix file, stderr was: " + result.stderr, 0, result.exitCode);
        assertTrue("expected formatted Nix on stdout, got: " + result.stdout,
                result.stdout.contains("languages.java.enable = true;"));
        assertNotEquals(unformatted, result.stdout);
        assertTrue("--quiet should keep treefmt's own logs off stdout",
                result.stdout.lines().noneMatch(line -> line.contains("traversed")));
    }

    @Test
    public void passesThroughFilesNoFormatterIsConfiguredFor() throws Exception {
        File treefmt = treefmt();
        String java = "class Foo {   int x; }\n";

        Result result = run(treefmt, "Foo.java", java);

        assertEquals(0, result.exitCode);
        assertEquals("an unmatched file must come back byte-identical", java, result.stdout);
    }

    private static Result run(File treefmt, String path, String input) throws Exception {
        Process process = new ProcessBuilder(treefmt.getAbsolutePath(), "--stdin", path, "--quiet")
                .directory(new File("."))
                .start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue("treefmt should answer quickly", process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return new Result(process.exitValue(), stdout, stderr);
    }

    private static File treefmt() throws Exception {
        Path root = new File(".").getCanonicalFile().toPath();
        assumeTrue("this test must run from the devenv-intellij checkout (its own devenv.nix is the fixture)",
                Files.isRegularFile(root.resolve("devenv.nix")));

        Path treefmt = root.resolve(".devenv/profile/bin/treefmt");
        assumeTrue("'devenv shell' must have built the profile for this project", Files.isExecutable(treefmt));
        return treefmt.toFile();
    }

    private record Result(int exitCode, String stdout, String stderr) {
    }
}
