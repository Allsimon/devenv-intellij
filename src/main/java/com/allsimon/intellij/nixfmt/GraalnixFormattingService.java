package com.allsimon.intellij.nixfmt;

import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.graalnix.format.Formatter;
import org.graalnix.parser.ParseException;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * Formats Nix files in process with graalnix's port of nixfmt. Registered after
 * {@link com.allsimon.intellij.treefmt.DevenvTreefmtFormattingService}, so a project whose treefmt
 * formats Nix keeps its own formatter.
 */
public final class GraalnixFormattingService extends AsyncDocumentFormattingService {
    @Override
    protected @NotNull String getName() {
        return "nixfmt";
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        return "Devenv";
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return EnumSet.noneOf(Feature.class);
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile != null && "nix".equals(virtualFile.getExtension());
    }

    @Override
    protected @NotNull FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        return new FormattingTask() {
            @Override
            public void run() {
                try {
                    request.onTextReady(Formatter.format(request.getDocumentText()));
                } catch (ParseException e) {
                    request.onError(MyMessageBundle.message("formatter.nixfmt.failed"), e.getMessage(), e.offset);
                }
            }

            @Override
            public boolean cancel() {
                return false;
            }
        };
    }
}
