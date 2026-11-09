package com.allsimon.intellij.core;

import com.intellij.execution.process.ProcessOutput;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DevenvCliTest {
    @Test
    public void failureIsTheErrorAtTheEndOfTheTrace() {
        ProcessOutput output = new ProcessOutput("",
                "\u2022 Validating lock\n  \033[31m\u00d7\033[0m Failed to get attribute 'config.cachix.enable':\n"
                        + "  \033[31m \033[0m \u2026 while evaluating a branch condition\n\n"
                        + "  \033[31m \033[0m \033[31;1merror:\033[0m function '\033[35;1manonymous lambda\033[0m'"
                        + " called with unexpected argument '\033[35;1mself\033[0m'\n",
                1, false, false);

        assertEquals("error: function 'anonymous lambda' called with unexpected argument 'self'",
                DevenvCli.failure(output));
    }

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
