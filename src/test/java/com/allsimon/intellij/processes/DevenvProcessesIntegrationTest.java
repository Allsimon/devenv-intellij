package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvCli;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    /**
     * {@link DevenvProcessLogTail} follows these files rather than re-running
     * {@code devenv processes logs}, which has no follow mode. Neither the {@code devenv.runtime}
     * attribute that locates them nor the naming under it is documented - they belong to the 'native'
     * process manager - so this is what would catch devenv moving them, at which point the plugin
     * silently falls back to the snapshot.
     * <p>
     * The directory is reached through {@code devenv eval devenv.runtime} rather than the
     * {@code .devenv/run} symlink, which devenv writes but does not keep up to date: it was seen
     * pointing at an hour-old runtime directory while the live manager wrote elsewhere.
     * <p>
     * Only the path and the naming are asserted. The two sources are not interchangeable: the command
     * answers from the manager, which was observed still reporting a previous run of a process minutes
     * after the file had been replaced by the current one - one more reason for the plugin to read the
     * files.
     */
    @Test
    public void theProcessManagerWritesAPerProcessLogFileWhereTheTailExpectsIt() throws Exception {
        File devenvRoot = devenvRoot();
        File executable = devenvExecutable();

        Process list = new ProcessBuilder(executable.getAbsolutePath(), "processes", "list")
                .directory(devenvRoot)
                .start();
        String listed = new String(list.getInputStream().readAllBytes());
        assumeTrue("this test needs a running process manager: start one with 'devenv up -d'",
                list.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES) && list.exitValue() == 0);
        Map<String, DevenvProcess> processes = DevenvProcessParser.parseList(listed);
        assumeTrue("the running process manager reports no process", !processes.isEmpty());

        Process eval = new ProcessBuilder(executable.getAbsolutePath(), "eval", "processes", "devenv.runtime")
                .directory(devenvRoot)
                .start();
        String evaluated = new String(eval.getInputStream().readAllBytes());
        assumeTrue("'devenv eval' must succeed to locate the runtime directory",
                eval.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES) && eval.exitValue() == 0);
        String runtime = DevenvProcessParser.parseRuntimeDirectory(evaluated);
        assertNotNull("'devenv eval devenv.runtime' should report a directory, got: " + evaluated, runtime);

        Path logDirectory = DevenvProcessLogTail.logDirectory(Path.of(runtime));
        assertNotNull("expected a log directory under " + runtime, logDirectory);

        // devenv only writes these for processes the running manager has actually started, so which of
        // them are there depends on what the checkout has been asked to run - the naming is the part
        // worth pinning, and stdout is what says a process has run at all.
        List<String> started = processes.keySet().stream()
                .filter(name -> Files.isRegularFile(logDirectory.resolve(name + ".stdout.log")))
                .toList();
        assumeTrue("no process has been started in this manager session: run 'devenv up -d'", !started.isEmpty());

        for (String name : started) {
            assertTrue("stdout and stderr logs should come as a pair, and '" + name + "' has no stderr one",
                    Files.isRegularFile(logDirectory.resolve(name + ".stderr.log")));
        }
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
