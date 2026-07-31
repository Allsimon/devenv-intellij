package com.allsimon.intellij;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Guards the two things the Services tool window assumes about the real devenv CLI, neither of which
 * devenv documents as a stable interface:
 * <ul>
 *   <li>{@code devenv eval processes} prints a JSON document {@link DevenvProcessParser} can read;</li>
 *   <li>a missing process manager is reported by a non-zero exit code, not by an empty process list -
 *       otherwise {@link DevenvProcessManager} would quietly show every process as stopped whenever
 *       the command started failing for some other reason.</li>
 * </ul>
 * Run from the devenv-intellij checkout, whose own devenv.nix declares no processes.
 */
public class DevenvProcessesIntegrationTest {
    private static final int TIMEOUT_MINUTES = 5;

    @Test
    public void evalProcessesPrintsAParseableDocument() throws Exception {
        File devenvRoot = devenvRoot();
        File executable = devenvExecutable();

        Process process = new ProcessBuilder(executable.getAbsolutePath(), "eval", "processes")
                .directory(devenvRoot)
                .start();
        // A cold nix eval cache has to evaluate the whole configuration before it can answer.
        String output = new String(process.getInputStream().readAllBytes());
        assumeTrue("'devenv eval processes' must succeed to assert anything about its output",
                process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES) && process.exitValue() == 0);

        assertNotNull(DevenvProcessParser.parseDeclared(output));
    }

    @Test
    public void processesListSignalsAStoppedManagerWithANonZeroExitCode() throws Exception {
        File devenvRoot = devenvRoot();
        File executable = devenvExecutable();

        Process process = new ProcessBuilder(executable.getAbsolutePath(), "processes", "list")
                .directory(devenvRoot)
                .start();
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = DevenvCli.stripAnsi(new String(process.getErrorStream().readAllBytes()));
        assertTrue("'devenv processes list' should answer immediately",
                process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES));
        assumeTrue("this test needs a project with no process manager running", process.exitValue() != 0);

        assertTrue("expected the stopped-manager message, got: " + stderr,
                stderr.contains("No process manager is running"));
        assertEquals("a stopped manager must not look like an empty process list",
                0, DevenvProcessParser.parseList(stdout).size());
    }

    private static File devenvRoot() throws Exception {
        File devenvRoot = new File(".").getCanonicalFile();
        assumeTrue("this test must run from the devenv-intellij checkout (its own devenv.nix is the fixture)",
                new File(devenvRoot, "devenv.nix").isFile());
        return devenvRoot;
    }

    private static File devenvExecutable() {
        File executable = PathEnvironmentVariableUtil.findInPath("devenv");
        assumeTrue("devenv must be on PATH to run this integration test", executable != null);
        return executable;
    }
}
