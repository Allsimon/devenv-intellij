package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.affogato.lint.Packages;
import org.affogato.lint.Packages.Package;
import org.affogato.lint.Packages.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Marks each package of a file that has a newer version, on its version, with its update as the fix. What it asks
 * affogato's updater is kept a while, so that it stays under GitHub's rate limit.
 */
public final class NixOutdatedAnnotator extends ExternalAnnotator<NixOutdatedAnnotator.Info, List<NixOutdatedAnnotator.Outdated>>
        implements DumbAware {
    record Info(String text) {}

    record Outdated(int start, int end, String name, String version) {}

    @Override
    public @Nullable Info collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        if (hasErrors) {
            return null;
        }
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem() || !file.getText().contains("fetch")) {
            return null;
        }
        return new Info(file.getText());
    }

    @Override
    public @Nullable List<Outdated> doAnnotate(Info info) {
        List<Package> packages;
        try {
            packages = Packages.find(info.text());
        } catch (RuntimeException e) {
            return null;
        }
        List<Outdated> out = new ArrayList<>();
        for (Package p : packages) {
            if (!p.updatable(false)) {
                continue;
            }
            String version;
            try {
                version = NixUpdates.newer(p);
            } catch (IOException | RuntimeException e) {
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return out;
            }
            if (version != null) {
                out.add(new Outdated(start(p), end(p), p.name() != null ? p.name() : "The package", version));
            }
        }
        return out;
    }

    /** Where the mark goes: the version, else the tag, else the start of the package. */
    private static int start(Package p) {
        if (p.versionStart() >= 0) {
            return p.versionStart();
        }
        Source s = p.sources().getFirst();
        return s.revStart() >= 0 ? s.revStart() : p.start();
    }

    private static int end(Package p) {
        if (p.versionStart() >= 0) {
            return p.versionEnd();
        }
        Source s = p.sources().getFirst();
        return s.revStart() >= 0 ? s.revEnd() : p.start() + 1;
    }

    @Override
    public void apply(@NotNull PsiFile file, List<Outdated> outdated, @NotNull AnnotationHolder holder) {
        if (outdated == null) {
            return;
        }
        int length = file.getTextLength();
        for (Outdated o : outdated) {
            if (o.end() > length) {
                continue;
            }
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, o.name() + " " + o.version() + " is available")
                    .range(new TextRange(o.start(), o.end()))
                    .withFix(new Fix(o))
                    .create();
        }
    }

    /** Runs the update intention at the package. */
    private static final class Fix implements IntentionAction {
        private final Outdated outdated;

        Fix(Outdated outdated) {
            this.outdated = outdated;
        }

        @Override
        public @NotNull String getText() {
            return "Update " + outdated.name() + " to " + outdated.version();
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Update the package";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return editor != null && element(file) != null;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
            PsiElement element = element(file);
            if (editor != null && element != null) {
                new NixUpdatePackageIntention().invoke(project, editor, element);
            }
        }

        private @Nullable PsiElement element(PsiFile file) {
            PsiElement element = file.findElementAt(outdated.start());
            return element != null && NixUpdatePackageIntention.target(element, false) != null ? element : null;
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        @Override
        public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor,
                                                             @NotNull PsiFile file) {
            return IntentionPreviewInfo.EMPTY;
        }
    }
}
