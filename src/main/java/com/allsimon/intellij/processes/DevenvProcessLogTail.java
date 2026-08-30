package com.allsimon.intellij.processes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Streams a process's output into a console by following the two files devenv's process manager
 * writes for it, instead of re-running {@code devenv processes logs} - which is a snapshot, with no
 * follow mode.
 * <p>
 * Following a growing file is what the IDE's own run-configuration log tabs do
 * ({@code LogConsoleBase}): hold the reader open, re-read whatever is ready on a short interval, and
 * reopen when the file is replaced. This does the same, on one pooled task shared by both streams, so
 * no {@code tail} process is spawned per node.
 */
final class DevenvProcessLogTail implements Disposable {
    private static final Logger LOG = Logger.getInstance(DevenvProcessLogTail.class);

    /**
     * Under the runtime directory devenv reports. Undocumented, and only written by the 'native'
     * {@code process.manager.implementation}.
     */
    private static final String LOG_DIRECTORY = "processes/logs";
    /**
     * The platform's log tabs re-read every 10ms. 100ms still reads as live to a human and costs a
     * fifth of the syscalls, and only while the node is selected.
     */
    private static final long INTERVAL_MILLIS = 100;
    /** How much of an already-written log to show before following it. */
    private static final long BACKLOG_BYTES = 64 * 1024;
    private static final int BUFFER_CHARS = 8 * 1024;
    /**
     * Cap on one read, so a process dumping megabytes doesn't build one string that big; the rest is
     * read on the next tick.
     */
    private static final int MAX_CHARS_PER_READ = 512 * 1024;
    /** A line this long with no newline in sight is emitted anyway, rather than held forever. */
    private static final int MAX_PENDING_CHARS = 8 * 1024;

    private final LogSink sink;
    private final List<Stream> streams;

    private volatile ScheduledFuture<?> poller;
    private volatile boolean disposed;

    DevenvProcessLogTail(@NotNull LogSink sink, @NotNull Path directory, @NotNull String processName) {
        this.sink = sink;
        this.streams = List.of(
                new Stream(directory.resolve(processName + ".stdout.log"), false),
                new Stream(directory.resolve(processName + ".stderr.log"), true));
    }

    /** Where the followed lines go. Kept narrow so the following itself can be tested without a UI. */
    interface LogSink {
        void appendLine(@NotNull String line, boolean stderr);

        /** The logs hold a new run; what was shown of the last one is no longer in them. */
        void reset();
    }

    /**
     * Where devenv's process manager writes per-process logs, given the runtime directory it reports,
     * or {@code null} when it writes none there.
     */
    static @Nullable Path logDirectory(@NotNull Path runtimeDirectory) {
        Path directory = runtimeDirectory.resolve(LOG_DIRECTORY);
        return Files.isDirectory(directory) ? directory : null;
    }

    /** Starts from the tail of whatever is already written, then follows. Idempotent. */
    void start() {
        if (disposed || poller != null) {
            return;
        }
        poller = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::pump, 0, INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    void stop() {
        ScheduledFuture<?> current = poller;
        poller = null;
        if (current != null) {
            current.cancel(false);
        }
        for (Stream stream : streams) {
            stream.close();
        }
    }

    /** scheduleWithFixedDelay stops rescheduling as soon as one run throws, so nothing may escape here. */
    void pump() {
        // Both logs are looked at before either is read: a restart rewrites the pair, and the console
        // has to be cleared once for both. Clearing per stream would throw away what the first of them
        // had just written.
        boolean startedOver = false;
        for (Stream stream : streams) {
            try {
                startedOver |= stream.observe();
            } catch (RuntimeException e) {
                LOG.warn("Failed to look at " + stream.path, e);
                stream.close();
            }
        }
        if (startedOver) {
            for (Stream stream : streams) {
                stream.close();
            }
            sink.reset();
        }

        for (Stream stream : streams) {
            try {
                stream.read();
            } catch (IOException | RuntimeException e) {
                LOG.warn("Failed to follow " + stream.path, e);
                stream.close();
            }
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        stop();
    }

    /** One of the two files, and how far into it we have read. */
    private final class Stream {
        private final Path path;
        private final boolean stderr;

        private Reader reader;
        /** Last size seen; a smaller one means the file was truncated and the reader has to start over. */
        private long knownSize;
        /** Identity of the file the reader is on, to notice it being replaced rather than truncated. */
        private Object knownKey;
        /** Last timestamp seen, which is all that moves when a run is rewritten to the same length. */
        private FileTime knownModified;
        /** What has been read since the last newline; the console works in whole lines. */
        private String pending = "";
        /** What the last {@link #observe()} found, handed to {@link #read()} so it stats once a tick. */
        private BasicFileAttributes current;

        private Stream(@NotNull Path path, boolean stderr) {
            this.path = path;
            this.stderr = stderr;
        }

        /** Takes the log's measurements and says whether they describe a new run. Reads nothing. */
        private boolean observe() {
            try {
                current = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (IOException e) {
                // Not started yet, or the manager cleaned up: wait for the file to come back, and read
                // it from the start when it does.
                current = null;
                close();
                return false;
            }
            if (!current.isRegularFile()) {
                current = null;
                close();
                return false;
            }
            return reader != null && startedOver(current);
        }

        /** Reads whatever {@link #observe()} found, opening the log first if it isn't open yet. */
        private void read() throws IOException {
            BasicFileAttributes attributes = current;
            if (attributes == null) {
                return;
            }
            if (reader == null) {
                // Show only the tail of an existing log, so selecting a long-running process doesn't
                // dump megabytes into the console.
                reader = open(Math.max(0, attributes.size() - BACKLOG_BYTES));
            }
            knownSize = attributes.size();
            knownKey = attributes.fileKey();
            knownModified = attributes.lastModifiedTime();
            drain();
        }

        /**
         * Whether the log holds a new run rather than more of the one being read.
         * <p>
         * Restarting a process can shorten the log, replace it outright - and a replacement that has
         * already outgrown the old one would slip past a size check, leaving the reader on an unlinked
         * file that never grows again - or rewrite it to exactly the same length under the same inode.
         * That last one is what any one-shot printing a fixed-width line does, {@code date} included,
         * and it shows up only as a newer timestamp on an unchanged size: an append would have changed
         * the size.
         */
        private boolean startedOver(@NotNull BasicFileAttributes attributes) {
            if (attributes.size() < knownSize || !Objects.equals(attributes.fileKey(), knownKey)) {
                return true;
            }
            return attributes.size() == knownSize
                    && knownModified != null
                    && attributes.lastModifiedTime().compareTo(knownModified) > 0;
        }

        private @NotNull Reader open(long skipBytes) throws IOException {
            InputStream stream = Files.newInputStream(path);
            try {
                long skipped = 0;
                while (skipped < skipBytes) {
                    long step = stream.skip(skipBytes - skipped);
                    if (step <= 0) {
                        break;
                    }
                    skipped += step;
                }
                Reader opened = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                if (skipped > 0) {
                    // Skipping lands mid-line, and possibly mid-character: drop the rest of that line,
                    // which takes any mangled character with it.
                    int character;
                    while ((character = opened.read()) != -1 && character != '\n') {
                        // Discarded on purpose.
                    }
                }
                return opened;
            } catch (IOException e) {
                stream.close();
                throw e;
            }
        }

        /**
         * Reads whatever is already buffered and prints it in one go. {@code ready()} keeps this from
         * blocking on a file the process has stopped writing to.
         */
        private void drain() throws IOException {
            char[] buffer = new char[BUFFER_CHARS];
            StringBuilder text = new StringBuilder();
            while (reader.ready() && text.length() < MAX_CHARS_PER_READ) {
                int read = reader.read(buffer);
                if (read <= 0) {
                    break;
                }
                text.append(buffer, 0, read);
            }
            if (!text.isEmpty()) {
                // The console buffers and flushes on the EDT itself, so a pooled thread may append.
                emit(text.toString());
            }
        }

        /** Cuts the chunk into whole lines, holding back whatever follows the last newline. */
        private void emit(@NotNull String chunk) {
            StringBuilder rest = new StringBuilder(pending).append(chunk);
            int start = 0;
            for (int i = 0; i < rest.length(); i++) {
                if (rest.charAt(i) == '\n') {
                    sink.appendLine(rest.substring(start, i), stderr);
                    start = i + 1;
                }
            }
            String leftover = rest.substring(start);
            if (leftover.length() >= MAX_PENDING_CHARS) {
                sink.appendLine(leftover, stderr);
                leftover = "";
            }
            pending = leftover;
        }

        private void close() {
            Reader current = reader;
            reader = null;
            knownSize = 0;
            knownKey = null;
            knownModified = null;
            pending = "";
            if (current != null) {
                try {
                    current.close();
                } catch (IOException e) {
                    LOG.warn("Failed to close " + path, e);
                }
            }
        }
    }
}
