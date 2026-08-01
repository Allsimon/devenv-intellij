package com.allsimon.intellij.lsp;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Drives the real `devenv lsp` process (nixd) over stdio, the same way the IntelliJ Platform LSP
 * client does, to guard the fix in {@link DevenvLspServerDescriptor#getWorkspaceConfiguration}:
 * nixd requests its configuration lazily via 'workspace/configuration' rather than accepting it
 * as a command-line argument, and silently falls back to plain NixOS options/nixpkgs if that
 * request goes unanswered - which made devenv module options like `languages.java.enable` fail to
 * resolve while plain nixpkgs attributes like `pkgs.gradle_9` kept working.
 */
public class DevenvLspIntegrationTest {
    private Process serverProcess;

    @After
    public void tearDown() {
        if (serverProcess != null) {
            serverProcess.destroyForcibly();
        }
    }

    @Test
    public void definitionResolvesDevenvModuleOption() throws Exception {
        File devenvRoot = new File(".").getCanonicalFile();
        assumeTrue("this test must run from the devenv-intellij checkout (its own devenv.nix is the fixture)",
                new File(devenvRoot, "devenv.nix").isFile());

        File executable = PathEnvironmentVariableUtil.findInPath("devenv");
        assumeTrue("devenv must be on PATH to run this integration test", executable != null);

        serverProcess = new ProcessBuilder(executable.getAbsolutePath(), "lsp")
                .directory(devenvRoot)
                .start();

        WorkspaceConfiguringClient client = new WorkspaceConfiguringClient(devenvRoot, executable);
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
                client, serverProcess.getInputStream(), serverProcess.getOutputStream());
        launcher.startListening();
        LanguageServer server = launcher.getRemoteProxy();

        InitializeParams initializeParams = new InitializeParams();
        initializeParams.setWorkspaceFolders(
                List.of(new WorkspaceFolder(devenvRoot.toURI().toString(), devenvRoot.getName())));
        WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
        workspaceCapabilities.setConfiguration(true);
        ClientCapabilities capabilities = new ClientCapabilities();
        capabilities.setWorkspace(workspaceCapabilities);
        initializeParams.setCapabilities(capabilities);
        server.initialize(initializeParams).get(60, TimeUnit.SECONDS);
        server.initialized(new InitializedParams());

        Path devenvNix = devenvRoot.toPath().resolve("devenv.nix");
        String text = Files.readString(devenvNix);
        String uri = devenvNix.toUri().toString();
        server.getTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(uri, "nix", 1, text)));

        String[] lines = text.split("\n", -1);
        int line = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("enable = true;")) {
                line = i;
                break;
            }
        }
        assertTrue("expected to find 'languages.java.enable' in this repo's devenv.nix", line >= 0);
        int character = lines[line].indexOf("enable");

        DefinitionParams definitionParams = new DefinitionParams(
                new TextDocumentIdentifier(uri), new Position(line, character));
        // First eval after a cold start builds nixd's option/nixpkgs attrset cache from scratch,
        // which can take minutes on an unwarmed store.
        Either<List<? extends Location>, List<? extends LocationLink>> result =
                server.getTextDocumentService().definition(definitionParams).get(5, TimeUnit.MINUTES);

        boolean resolved = result != null
                && ((result.isLeft() && !result.getLeft().isEmpty())
                    || (result.isRight() && !result.getRight().isEmpty()));
        assertTrue("expected 'languages.java.enable' to resolve to a definition, got: " + result, resolved);

        server.shutdown().get(10, TimeUnit.SECONDS);
        server.exit();
    }

    /**
     * Answers nixd's 'workspace/configuration' pull request with the "nixd" section of
     * {@code devenv lsp --print-config}, reusing {@link DevenvLspServerDescriptor#extractSection}
     * so the test exercises the exact JSON handling the plugin ships.
     */
    private static final class WorkspaceConfiguringClient implements LanguageClient {
        private final File devenvRoot;
        private final File executable;

        WorkspaceConfiguringClient(File devenvRoot, File executable) {
            this.devenvRoot = devenvRoot;
            this.executable = executable;
        }

        @Override
        public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
            List<Object> result = new ArrayList<>();
            for (ConfigurationItem item : params.getItems()) {
                result.add("nixd".equals(item.getSection()) ? fetchNixdConfiguration() : null);
            }
            return CompletableFuture.completedFuture(result);
        }

        private Object fetchNixdConfiguration() {
            try {
                Process printConfig = new ProcessBuilder(executable.getAbsolutePath(), "lsp", "--print-config")
                        .directory(devenvRoot)
                        .start();
                String output = new String(printConfig.getInputStream().readAllBytes());
                printConfig.waitFor(60, TimeUnit.SECONDS);
                return DevenvLspServerDescriptor.extractSection(output, "nixd");
            } catch (Exception e) {
                throw new RuntimeException("failed to run 'devenv lsp --print-config'", e);
            }
        }

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }
    }
}
