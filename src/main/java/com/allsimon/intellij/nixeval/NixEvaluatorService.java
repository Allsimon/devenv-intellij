package com.allsimon.intellij.nixeval;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.nixlang.NixInjectionSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.affogato.evaluator.SessionEvaluator;
import org.affogato.lsp.Evaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * nixpkgs and the NixOS options, evaluated in the IDE by affogato's interpreter, for completion, documentation,
 * navigation and inlay hints. Started on first use, and closed once unused for a while to give its memory back.
 */
@Service(Service.Level.PROJECT)
public final class NixEvaluatorService implements Disposable {
    private static final Logger LOG = Logger.getInstance(NixEvaluatorService.class);

    private static final String NIXOS_OPTIONS = "(import <nixpkgs/nixos> { configuration = { }; }).options";
    private static final long IDLE_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int PRINT_CONFIG_TIMEOUT_MILLIS = 120_000;
    private static final int COPY_TIMEOUT_MILLIS = 600_000;

    private final Project project;
    private SessionEvaluator session;
    private ScheduledFuture<?> idleCheck;
    private long used;
    private Evaluator replacement;

    public NixEvaluatorService(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull NixEvaluatorService getInstance(@NotNull Project project) {
        return project.getService(NixEvaluatorService.class);
    }

    /** The evaluator, started if need be, or null when evaluation is switched off, as in tests unless replaced. */
    public synchronized @Nullable Evaluator evaluator() {
        if (replacement != null) {
            return replacement;
        }
        NixInjectionSettings settings = NixInjectionSettings.getInstance();
        if (!settings.evaluate || ApplicationManager.getApplication().isUnitTestMode()) {
            return null;
        }
        used = System.currentTimeMillis();
        if (session == null) {
            start(settings.nixpkgs);
        }
        return session.main();
    }

    private void start(String nixpkgs) {
        String base = project.getBasePath();
        session = new SessionEvaluator(
                nixpkgs,
                Map.of("nixos", NIXOS_OPTIONS),
                base != null ? base : System.getProperty("user.home"),
                Objects.requireNonNullElse(EnvironmentUtil.getValue("NIX_PATH"), ""),
                NixEvaluatorService::printConfig);
        session.main().progress(this::begin);
        session.main().failures(this::warn);
        session.inputs(NixEvaluatorService::copyInput);
        idleCheck = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::closeIfIdle, 1, 1, TimeUnit.MINUTES);
    }

    private synchronized void closeIfIdle() {
        if (session != null && System.currentTimeMillis() - used > IDLE_MILLIS) {
            stop();
        }
    }

    private void stop() {
        idleCheck.cancel(false);
        SessionEvaluator closed = session;
        session = null;
        closed.close();
    }

    /** Drops the evaluation, as the settings it was started with changed. */
    public synchronized void restart() {
        if (session != null) {
            stop();
        }
    }

    /** Drops the values that may depend on the file at {@code path}, which was written. */
    public synchronized void saved(@NotNull String path) {
        if (session != null) {
            session.main().saved(path);
        }
    }

    private Runnable begin(String title) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (done.isDone() || project.isDisposed()) {
                return;
            }
            new Task.Backgroundable(project, title, false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    done.join();
                }
            }.queue();
        });
        return () -> done.complete(null);
    }

    private void warn(String title, String message) {
        if (project.isDisposed()) {
            return;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Devenv")
                .createNotification(title + " failed", message, NotificationType.WARNING)
                .notify(project);
    }

    /** Has Nix copy a locked flake input from its fetch cache into the store, without the network. */
    private static boolean copyInput(String ref) {
        File nix = PathEnvironmentVariableUtil.findInPath("nix");
        if (nix == null) {
            return false;
        }
        GeneralCommandLine commandLine = new GeneralCommandLine(SessionEvaluator.copyCommand(nix.getPath(), ref))
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withEnvironment(SessionEvaluator.OFFLINE);
        try {
            ProcessOutput output = ExecUtil.execAndGetOutput(commandLine, COPY_TIMEOUT_MILLIS);
            if (output.getExitCode() != 0) {
                LOG.info("Could not copy " + ref + " into the store: " + DevenvCli.failure(output));
            }
            return output.getExitCode() == 0;
        } catch (ExecutionException e) {
            LOG.warn("Failed to run " + commandLine.getCommandLineString(), e);
            return false;
        }
    }

    private static @Nullable String printConfig(String root) {
        File executable = DevenvCli.findExecutable();
        if (executable == null) {
            return null;
        }
        try {
            ProcessOutput output = DevenvCli.run(
                    DevenvCli.commandLine(executable, root, "lsp", "--print-config"), PRINT_CONFIG_TIMEOUT_MILLIS);
            if (output.getExitCode() != 0) {
                LOG.warn("'devenv lsp --print-config' exited with " + output.getExitCode() + ": " + DevenvCli.failure(output));
                return null;
            }
            return output.getStdout();
        } catch (ExecutionException e) {
            LOG.warn("Failed to run 'devenv lsp --print-config'", e);
            return null;
        }
    }

    @TestOnly
    public synchronized void replaceEvaluator(@NotNull Evaluator evaluator, @NotNull Disposable parent) {
        replacement = evaluator;
        Disposer.register(parent, () -> {
            synchronized (this) {
                replacement = null;
            }
        });
    }

    @Override
    public synchronized void dispose() {
        if (session != null) {
            stop();
        }
    }
}
