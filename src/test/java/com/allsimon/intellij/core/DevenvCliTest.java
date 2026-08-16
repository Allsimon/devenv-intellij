package com.allsimon.intellij.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DevenvCliTest {
    @Test
    public void stripAnsiRemovesColourCodes() {
        assertEquals("  x No process manager is running.",
                DevenvCli.stripAnsi("  \033[31mx\033[0m No process manager is running."));
    }

    @Test
    public void stripAnsiLeavesPlainOutputAlone() {
        String plain = "ticker                         ready restarts: 0\n";

        assertEquals(plain, DevenvCli.stripAnsi(plain));
    }

    @Test
    public void stripAnsiRemovesCursorAndEraseSequences() {
        assertEquals("done", DevenvCli.stripAnsi("\033[2K\033[1;32mdone\033[m"));
    }
}
