package com.allsimon.intellij.treefmt;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

/**
 * Formats a devenv project's files with the treefmt that project declares, so that Reformat Code and
 * the treefmt git hook can't disagree.
 * <p>
 * Only files treefmt is actually configured for are claimed - see {@link TreefmtConfig#matches} -
 * because claiming a file here takes it away from the IDE's own formatter. In this repo, for
 * instance, treefmt formats Nix and Markdown, and Java keeps using IntelliJ's Java formatter.
 */
public final class DevenvTreefmtFormattingService extends AsyncDocumentFormattingService {
    private static final Logger LOG = Logger.getInstance(DevenvTreefmtFormattingService.class);

    private static final String NAME = "treefmt";
    private static final String NOTIFICATION_GROUP = "Devenv";

    @Override
    protected @NotNull String getName() {
        return NAME;
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        return NOTIFICATION_GROUP;
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        // treefmt reformats whole files: it can neither format a fragment nor optimize imports.
        return EnumSet.noneOf(Feature.class);
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
            return false;
        }
        DevenvTreefmt treefmt = DevenvTreefmt.resolve(file.getProject());
        return treefmt != null && treefmt.canFormat(Path.of(virtualFile.getPath()));
    }

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        VirtualFile virtualFile = request.getContext().getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        DevenvTreefmt treefmt = DevenvTreefmt.resolve(request.getContext().getProject());
        if (treefmt == null) {
            return null;
        }

        Path file = Path.of(virtualFile.getPath());
        if (!treefmt.canFormat(file)) {
            return null;
        }
        return new TreefmtFormattingTask(request, treefmt, file, (int) getTimeout().toMillis());
    }

    /**
     * Runs the document through {@code treefmt --stdin}, which picks the formatter from the path,
     * writes the result to stdout and leaves its own logs on stderr. Going through stdin rather than
     * the file on disk means unsaved editor changes are formatted and nothing is written behind the
     * IDE's back.
     */
    private static final class TreefmtFormattingTask implements FormattingTask {
        private final AsyncFormattingRequest request;
        private final DevenvTreefmt treefmt;
        private final Path file;
        private final int timeoutMillis;

        private volatile CapturingProcessHandler handler;

        private TreefmtFormattingTask(@NotNull AsyncFormattingRequest request, @NotNull DevenvTreefmt treefmt,
                                      @NotNull Path file, int timeoutMillis) {
            this.request = request;
            this.treefmt = treefmt;
            this.file = file;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void run() {
            String documentText = request.getDocumentText();
            String relativePath = treefmt.devenvRoot().relativize(file).toString();
            GeneralCommandLine commandLine = DevenvCli.commandLine(
                    treefmt.executable().toFile(), treefmt.devenvRoot().toString(),
                    "--stdin", relativePath, "--quiet");

            try {
                ProcessOutput output = execute(commandLine, documentText);
                if (output.isTimeout()) {
                    fail(MyMessageBundle.message("formatter.treefmt.timedOut"));
                    return;
                }
                if (output.getExitCode() != 0) {
                    fail(DevenvCli.stripAnsi(output.getStderr()).strip());
                    return;
                }

                String formatted = output.getStdout();
                if (formatted.isEmpty() && !documentText.isEmpty()) {
                    // treefmt reported success but produced nothing; accepting that would empty the file.
                    fail(MyMessageBundle.message("formatter.treefmt.emptyOutput"));
                    return;
                }
                request.onTextReady(formatted);
            } catch (ExecutionException | RuntimeException e) {
                LOG.warn("Failed to run '" + commandLine.getCommandLineString() + "'", e);
                fail(String.valueOf(e.getMessage()));
            }
        }

        private void fail(@NotNull String message) {
            request.onError(MyMessageBundle.message("formatter.treefmt.failed"), message);
        }

        private @NotNull ProcessOutput execute(@NotNull GeneralCommandLine commandLine, @NotNull String documentText)
                throws ExecutionException {
            CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine);
            handler = processHandler;
            // Feed stdin only once the handler has started draining stdout, so that a document larger
            // than the pipe buffer can't deadlock the two sides against each other.
            processHandler.addProcessListener(new ProcessListener() {
                @Override
                public void startNotified(@NotNull ProcessEvent event) {
                    try (OutputStream input = processHandler.getProcessInput()) {
                        input.write(documentText.getBytes(processHandler.getCharset()));
                    } catch (IOException e) {
                        LOG.warn("Failed to pass the document to treefmt", e);
                    }
                }
            });
            return processHandler.runProcess(timeoutMillis);
        }

        @Override
        public boolean cancel() {
            CapturingProcessHandler processHandler = handler;
            if (processHandler == null) {
                return false;
            }
            processHandler.destroyProcess();
            return true;
        }
    }
}
