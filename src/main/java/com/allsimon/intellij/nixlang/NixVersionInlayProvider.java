package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hints.declarative.HintFormat;
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector;
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider;
import com.intellij.codeInsight.hints.declarative.InlayTreeSink;
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition;
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import kotlin.Unit;
import org.affogato.lsp.Analysis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The version of each package selected from nixpkgs, after its selection. */
public final class NixVersionInlayProvider implements InlayHintsProvider {
    @Override
    public @Nullable InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
        return file instanceof NixFile ? (OwnBypassCollector) NixVersionInlayProvider::collect : null;
    }

    private static void collect(PsiFile file, InlayTreeSink sink) {
        Analysis analysis = NixAnalyses.of(file);
        Analysis.Hints hints = analysis.inlayHints(0, file.getTextLength());
        for (Analysis.Hint hint : hints.hints()) {
            sink.addPresentation(
                    new InlineInlayPosition(hint.offset(), true, 0), null, null, HintFormat.Companion.getDefault(),
                    builder -> {
                        builder.text(hint.label(), null);
                        return Unit.INSTANCE;
                    });
        }
        if (!hints.pending().isEmpty()) {
            analysis.evaluator().whenDescribed(hints.pending(), () -> ApplicationManager.getApplication().invokeLater(() -> {
                if (file.isValid()) {
                    DaemonCodeAnalyzer.getInstance(file.getProject()).restart(file);
                }
            }));
        }
    }
}
