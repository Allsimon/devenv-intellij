package com.allsimon.intellij.lsp;

import com.allsimon.intellij.core.DevenvCli;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspServer;
import com.intellij.platform.lsp.api.LspServerSupportProvider;
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Backs Nix files with the language server started by {@code devenv lsp}, so that devenv options get
 * completion, documentation and diagnostics inside 'devenv.nix' and the files it imports.
 */
public final class DevenvLspServerSupportProvider implements LspServerSupportProvider {

    @Override
    public void fileOpened(@NotNull Project project, @NotNull VirtualFile file,
                           @NotNull LspServerStarter serverStarter) {
        if (!DevenvLspServerDescriptor.isNixFile(file)) {
            return;
        }

        VirtualFile devenvRoot = DevenvCli.findDevenvRoot(project);
        if (devenvRoot == null) {
            return;
        }

        serverStarter.ensureServerStarted(new DevenvLspServerDescriptor(project, devenvRoot));
    }

    @Override
    public LspServerWidgetItem createLspServerWidgetItem(@NotNull LspServer lspServer,
                                                         @Nullable VirtualFile currentFile) {
        return new LspServerWidgetItem(lspServer, currentFile, AllIcons.FileTypes.Config, null);
    }
}
