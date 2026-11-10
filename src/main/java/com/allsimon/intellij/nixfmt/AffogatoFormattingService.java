package com.allsimon.intellij.nixfmt;

import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.TextRange;
import org.affogato.format.Formatter;
import org.affogato.lsp.Analysis;
import org.affogato.parser.ParseException;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Formats Nix files in process with affogato's port of nixfmt. Registered after
 * {@link com.allsimon.intellij.treefmt.DevenvTreefmtFormattingService}, so a project whose treefmt
 * formats Nix keeps its own formatter.
 */
public final class AffogatoFormattingService extends AsyncDocumentFormattingService {
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
        return EnumSet.of(Feature.FORMAT_FRAGMENTS);
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        // An injected fragment, such as the Bash of a phase, is named after its host.
        return virtualFile != null && "nix".equals(virtualFile.getExtension())
                && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
    }

    @Override
    protected @NotNull FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        return new FormattingTask() {
            @Override
            public void run() {
                String text = request.getDocumentText();
                List<TextRange> ranges = request.getFormattingRanges();
                try {
                    String formatted = ranges.size() == 1 && !ranges.getFirst().equals(TextRange.allOf(text))
                            ? formatRange(text, ranges.getFirst())
                            : Formatter.format(text);
                    // The platform sets any text it gets, which marks an unchanged file as modified.
                    request.onTextReady(formatted.equals(text) ? null : formatted);
                } catch (ParseException e) {
                    request.onError(MyMessageBundle.message("formatter.nixfmt.failed"), e.getMessage(), e.offset);
                }
            }

            /** The text with the bindings the range touches formatted, or all of it outside any; unchanged if it does not parse. */
            private static String formatRange(String text, TextRange range) {
                Analysis.Rewrite rewrite = Analysis.formatRange(
                        text, range.getStartOffset(), range.getEndOffset(), Formatter.DEFAULT_WIDTH, Formatter.DEFAULT_INDENT, false);
                return rewrite == null ? text : text.substring(0, rewrite.start()) + rewrite.text() + text.substring(rewrite.end());
            }

            @Override
            public boolean cancel() {
                return false;
            }
        };
    }
}
