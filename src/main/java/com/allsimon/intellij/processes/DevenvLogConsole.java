package com.allsimon.intellij.processes;

import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFormatter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;

/**
 * The console a process node shows: the platform's log console, so the text filter and the stream
 * selector above it come for free, along with the ANSI decoding and the line buffer behind them.
 * <p>
 * It is normally given a file to read; this one is fed by {@link DevenvProcessLogTail} instead - two
 * files at once - so it gets an empty reader and is never {@code activate()}d, which is what would
 * otherwise start a reader thread of its own.
 */
final class DevenvLogConsole extends LogConsoleBase implements DevenvProcessLogTail.LogSink {
    /**
     * Prefixed to a line to record that it came from stderr. The filter model reads it back off the
     * stored line, which is the only thing it is given when the console re-filters.
     */
    static final String STDERR_MARK = String.valueOf((char) 1);

    DevenvLogConsole(@NotNull Project project, @NotNull String title) {
        super(project, Reader.nullReader(), title, true, new DevenvLogFilterModel(),
                GlobalSearchScope.allScope(project), new StripMarkFormatter());
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void appendLine(@NotNull String line, boolean stderr) {
        // addMessage adds the newline itself, and expects one line at a time - that is the unit the
        // filter works in.
        addMessage(stderr ? STDERR_MARK + line : line);
    }

    @Override
    public void reset() {
        // The log holds a new run and no longer holds the one on screen, so showing both would invent
        // a history the process never had - a one-shot would grow a line per start.
        clear();
    }

    /** For the one-shot snapshot, which arrives as one block rather than as it is produced. */
    void appendText(@NotNull String text, boolean stderr) {
        for (String line : text.lines().toList()) {
            appendLine(line, stderr);
        }
    }

    /** Strips the stderr marker again, once the model has read what it needed from it. */
    static final class StripMarkFormatter implements LogFormatter {
        @Override
        public @NotNull String formatMessage(@NotNull String line) {
            return line.startsWith(STDERR_MARK) ? line.substring(STDERR_MARK.length()) : line;
        }

        @Override
        public @NotNull String formatPrefix(@NotNull String prefix) {
            return prefix;
        }
    }
}
