package com.allsimon.intellij.lsp;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspServerDescriptor;
import org.eclipse.lsp4j.ConfigurationItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Describes the language server started by {@code devenv lsp}: a nixd instance preconfigured with the
 * devenv module options and the nixpkgs pinned by the devenv.lock of one root.
 * <p>
 * Rooted at that directory rather than at the project: the platform identifies a server by its
 * descriptor's class, name and roots, so a project holding several devenv.nix gets one server each,
 * and every one of them answers only for the files of its own root.
 */
final class DevenvLspServerDescriptor extends LspServerDescriptor {
    private static final Logger LOG = Logger.getInstance(DevenvLspServerDescriptor.class);

    private static final String NIX_EXTENSION = "nix";

    // nixd doesn't take the config that `devenv lsp --print-config` computes (pointing it at the
    // devenv module's own options and the nixpkgs pinned by devenv.lock) via a command-line flag: it
    // pulls it lazily from the LSP client with a 'workspace/configuration' request for this section.
    // Left unanswered, nixd falls back to evaluating plain NixOS options/nixpkgs instead of devenv's,
    // which is why devenv-specific attributes like 'languages.java.enable' fail to resolve.
    private static final String NIXD_CONFIGURATION_SECTION = "nixd";

    private final VirtualFile devenvRoot;
    private volatile JsonObject nixdConfiguration;

    DevenvLspServerDescriptor(@NotNull Project project, @NotNull VirtualFile devenvRoot) {
        super(project, MyMessageBundle.message("lsp.devenv.presentableName"), devenvRoot);
        this.devenvRoot = devenvRoot;
    }

    static boolean isNixFile(@NotNull VirtualFile file) {
        return file.isInLocalFileSystem() && NIX_EXTENSION.equals(file.getExtension());
    }

    /**
     * The files of this root and no other. The cheap containment test comes first because this runs
     * for every file the LSP client considers; the second one settles nested roots, where a file is
     * held by two of them and belongs to the innermost.
     */
    @Override
    public boolean isSupportedFile(@NotNull VirtualFile file) {
        return isNixFile(file)
                && VfsUtilCore.isAncestor(devenvRoot, file, false)
                && devenvRoot.equals(DevenvCli.findDevenvRootFor(getProject(), file));
    }

    @Override
    public @Nullable Object getWorkspaceConfiguration(@NotNull ConfigurationItem item) {
        if (!NIXD_CONFIGURATION_SECTION.equals(item.getSection())) {
            return null;
        }
        JsonObject configuration = nixdConfiguration;
        if (configuration == null) {
            configuration = fetchNixdConfiguration();
            nixdConfiguration = configuration;
        }
        return configuration;
    }

    private @Nullable JsonObject fetchNixdConfiguration() {
        File executable = DevenvCli.findExecutable();
        if (executable == null) {
            return null;
        }

        GeneralCommandLine commandLine =
                DevenvCli.commandLine(executable, devenvRoot.getPath(), "lsp", "--print-config");
        try {
            ProcessOutput output = DevenvCli.run(commandLine);
            if (output.getExitCode() != 0) {
                LOG.warn("'devenv lsp --print-config' exited with " + output.getExitCode() + ": " + output.getStderr());
                return null;
            }
            return extractSection(output.getStdout(), NIXD_CONFIGURATION_SECTION);
        } catch (ExecutionException | RuntimeException e) {
            LOG.warn("Failed to read nixd configuration from 'devenv lsp --print-config'", e);
            return null;
        }
    }

    /** Split out from {@link #fetchNixdConfiguration()} so the JSON handling can be unit-tested without spawning a process. */
    static @Nullable JsonObject extractSection(@NotNull String printConfigOutput, @NotNull String section) {
        JsonElement root = JsonParser.parseString(printConfigOutput);
        if (!root.isJsonObject()) {
            return null;
        }
        JsonElement value = root.getAsJsonObject().get(section);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    @Override
    public @NotNull GeneralCommandLine createCommandLine() throws ExecutionException {
        return createCommandLine(DevenvCli.findExecutable());
    }

    /** Split out from {@link #createCommandLine()} so tests can supply an executable without touching PATH. */
    @NotNull GeneralCommandLine createCommandLine(@Nullable File executable) throws ExecutionException {
        if (executable == null) {
            throw new ExecutionException(MyMessageBundle.message("lsp.devenv.executableNotFound", DevenvCli.EXECUTABLE));
        }
        return DevenvCli.commandLine(executable, devenvRoot.getPath(), "lsp");
    }
}
