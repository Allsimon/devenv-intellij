package com.allsimon.intellij.processes;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DevenvProcessParserTest {
    /**
     * Verbatim output of 'devenv processes list' (devenv 2.2.0) for a devenv.nix declaring a
     * long-running 'ticker' and a one-shot 'oneshot'. The name column is blank-padded; there is no
     * header row and no exit code.
     */
    private static final String LIST_OUTPUT = """
            oneshot                        exited restarts: 0
            ticker                         ready restarts: 0
            """;

    @Test
    public void parseDeclaredReadsNamesAndExec() {
        String evalOutput = """
                {"processes":{"ticker":{"exec":"while true; do echo tick; sleep 1; done"},"oneshot":{"exec":"echo done"}}}""";

        List<DevenvProcess> declared = DevenvProcessParser.parseDeclared(evalOutput);

        assertEquals(List.of("ticker", "oneshot"), declared.stream().map(DevenvProcess::name).toList());
        assertEquals("echo done", declared.get(1).exec());
        assertEquals(DevenvProcess.Status.NOT_STARTED, declared.get(0).status());
    }

    @Test
    public void parseDeclaredReturnsEmptyListWhenNoProcessesAreDeclared() {
        assertTrue(DevenvProcessParser.parseDeclared("{\"processes\":{}}").isEmpty());
    }

    @Test
    public void parseDeclaredToleratesProcessesWithoutExec() {
        List<DevenvProcess> declared = DevenvProcessParser.parseDeclared("{\"processes\":{\"ticker\":\"not-an-object\"}}");

        assertEquals(1, declared.size());
        assertNull(declared.get(0).exec());
    }

    @Test
    public void parseDeclaredReturnsEmptyListWhenOutputIsNotAProcessesDocument() {
        assertTrue(DevenvProcessParser.parseDeclared("[]").isEmpty());
        assertTrue(DevenvProcessParser.parseDeclared("{\"tasks\":{}}").isEmpty());
    }

    @Test
    public void parseListReadsNameStatusAndRestarts() {
        Map<String, DevenvProcess> reported = DevenvProcessParser.parseList(LIST_OUTPUT);

        assertEquals(List.of("oneshot", "ticker"), List.copyOf(reported.keySet()));
        assertEquals(DevenvProcess.Status.EXITED, reported.get("oneshot").status());
        assertEquals(DevenvProcess.Status.READY, reported.get("ticker").status());
        assertEquals(Integer.valueOf(0), reported.get("ticker").restarts());
    }

    @Test
    public void parseListReadsSnakeCasePhases() {
        Map<String, DevenvProcess> reported = DevenvProcessParser.parseList("""
                ticker                         not_started restarts: 0
                worker                         gave_up restarts: 3
                """);

        assertEquals(DevenvProcess.Status.NOT_STARTED, reported.get("ticker").status());
        assertEquals(DevenvProcess.Status.GAVE_UP, reported.get("worker").status());
        assertEquals(Integer.valueOf(3), reported.get("worker").restarts());
    }

    @Test
    public void parseListStripsAnsiEscapes() {
        Map<String, DevenvProcess> reported =
                DevenvProcessParser.parseList("\033[32mticker\033[0m                ready restarts: 0\n");

        assertEquals(DevenvProcess.Status.READY, reported.get("ticker").status());
    }

    @Test
    public void parseListMapsUnknownPhasesToUnknownRatherThanFailing() {
        Map<String, DevenvProcess> reported = DevenvProcessParser.parseList("ticker    something-new restarts: 0\n");

        assertEquals(DevenvProcess.Status.UNKNOWN, reported.get("ticker").status());
    }

    @Test
    public void parseListReadsRowsWithoutARestartCount() {
        Map<String, DevenvProcess> reported = DevenvProcessParser.parseList("ticker    ready\n");

        assertEquals(DevenvProcess.Status.READY, reported.get("ticker").status());
        assertNull(reported.get("ticker").restarts());
    }

    @Test
    public void parseListReturnsNothingWhenTheManagerReportsNoProcesses() {
        assertTrue(DevenvProcessParser.parseList("No processes found.\n").isEmpty());
        assertTrue(DevenvProcessParser.parseList("").isEmpty());
    }
}
