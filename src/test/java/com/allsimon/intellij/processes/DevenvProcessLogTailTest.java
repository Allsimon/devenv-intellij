package com.allsimon.intellij.processes;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The following itself, driven a pump at a time rather than on its timer, so the cases that only show
 * up when a process is restarted are reproducible.
 * <p>
 * These are the ones that made the console look broken: the tail has to notice a log being truncated
 * <b>and</b> one being replaced outright - and a replacement can already be longer than what it
 * replaced, which is why the size alone is not enough to tell.
 */
public class DevenvProcessLogTailTest {
    private static final String PROCESS = "ticker";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final RecordingSink sink = new RecordingSink();
    private Path directory;
    private Path stdout;
    private Path stderr;
    private DevenvProcessLogTail tail;

    @Before
    public void setUp() throws IOException {
        directory = folder.newFolder("logs").toPath();
        stdout = directory.resolve(PROCESS + ".stdout.log");
        stderr = directory.resolve(PROCESS + ".stderr.log");
        tail = new DevenvProcessLogTail(sink, directory, PROCESS);
    }

    @Test
    public void readsNothingWhileTheProcessHasNotRun() {
        tail.pump();

        assertTrue("a process that has never run has no log files at all", sink.lines.isEmpty());
    }

    @Test
    public void picksUpALogThatOnlyAppearsAfterTheProcessIsStarted() throws IOException {
        tail.pump();

        write(stdout, "first\nsecond\n");
        tail.pump();

        assertEquals(List.of("out:first", "out:second"), sink.lines);
    }

    @Test
    public void followsWhatIsAppendedAfterwards() throws IOException {
        write(stdout, "first\n");
        tail.pump();

        append(stdout, "second\n");
        tail.pump();

        assertEquals(List.of("out:first", "out:second"), sink.lines);
    }

    @Test
    public void keepsBothStreamsApart() throws IOException {
        write(stdout, "out\n");
        write(stderr, "err\n");

        tail.pump();

        assertEquals(List.of("out:out", "err:err"), sink.lines);
    }

    @Test
    public void holdsBackAPartialLineUntilItsNewlineArrives() throws IOException {
        write(stdout, "progress: ");
        tail.pump();
        assertEquals("a line without its newline is not a line yet", List.of(), sink.lines);

        append(stdout, "done\n");
        tail.pump();

        assertEquals(List.of("out:progress: done"), sink.lines);
    }

    @Test
    public void startsOverWhenARestartTruncatesTheLog() throws IOException {
        write(stdout, "a line from the run that has just ended\n");
        tail.pump();
        sink.lines.clear();

        // What 'devenv processes start' does to a log it keeps: same file, back to nothing.
        write(stdout, "new run\n");
        tail.pump();

        assertEquals(List.of("--reset--", "out:new run"), sink.lines);
    }

    /** The case a size check misses, and the one that left the console stuck until 'Refresh Logs'. */
    @Test
    public void startsOverWhenARestartReplacesTheLogWithALongerOne() throws IOException {
        write(stdout, "short\n");
        tail.pump();
        sink.lines.clear();

        Files.delete(stdout);
        write(stdout, "a much longer line from the new run\n");
        tail.pump();

        assertEquals(List.of("--reset--", "out:a much longer line from the new run"), sink.lines);
    }

    /**
     * What a one-shot does on every run: same file, same inode, and - because its output is a
     * fixed-width line - the same byte count, so only the timestamp says anything happened. This is
     * 'processes.foo.exec = "date"', whose 33 bytes never change length.
     * <p>
     * The reset matters as much as the new line: a one-shot's log holds one run, so a console that
     * only ever appended would show a line per start and none of them would be in the file.
     */
    @Test
    public void startsOverWhenAOneShotRewritesItsLogToTheSameLength() throws IOException {
        write(stdout, "Sun 30 Aug 2026 15:51:39\n");
        tail.pump();
        sink.lines.clear();

        write(stdout, "Sun 30 Aug 2026 15:52:07\n");
        touchLater(stdout);
        tail.pump();

        assertEquals(List.of("--reset--", "out:Sun 30 Aug 2026 15:52:07"), sink.lines);
    }

    @Test
    public void keepsWhatIsOnScreenWhileTheProcessIsOnlyAppending() throws IOException {
        write(stdout, "first\n");
        tail.pump();

        append(stdout, "second\n");
        tail.pump();

        assertEquals("appending is not a new run, so nothing on screen is stale",
                List.of("out:first", "out:second"), sink.lines);
    }

    /** One reset for the pair, or whichever stream is read first has its new lines wiped by the other. */
    @Test
    public void clearsOnceWhenBothLogsAreRewrittenTogether() throws IOException {
        write(stdout, "old out\n");
        write(stderr, "old err\n");
        tail.pump();
        sink.lines.clear();

        write(stdout, "new out\n");
        write(stderr, "new err\n");
        touchLater(stdout);
        touchLater(stderr);
        tail.pump();

        assertEquals(List.of("--reset--", "out:new out", "err:new err"), sink.lines);
    }

    @Test
    public void waitsForTheLogToComeBackWhenItIsRemoved() throws IOException {
        write(stdout, "before\n");
        tail.pump();
        sink.lines.clear();

        Files.delete(stdout);
        tail.pump();
        assertEquals(List.of(), sink.lines);

        write(stdout, "after\n");
        tail.pump();

        assertEquals(List.of("out:after"), sink.lines);
    }

    private static void write(@NotNull Path path, @NotNull String text)
            throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void append(@NotNull Path path, @NotNull String text)
            throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    /** Puts the timestamp unambiguously ahead, so the test doesn't ride on the clock's resolution. */
    private static void touchLater(@NotNull Path path) throws IOException {
        Files.setLastModifiedTime(path, FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis() + 1_000));
    }

    /** Records resets in the same list as the lines, so their order relative to them is asserted too. */
    private static final class RecordingSink implements DevenvProcessLogTail.LogSink {
        private static final String RESET = "--reset--";

        private final List<String> lines = new ArrayList<>();

        @Override
        public void appendLine(@NotNull String line, boolean stderr) {
            lines.add((stderr ? "err:" : "out:") + line);
        }

        @Override
        public void reset() {
            lines.add(RESET);
        }
    }
}
