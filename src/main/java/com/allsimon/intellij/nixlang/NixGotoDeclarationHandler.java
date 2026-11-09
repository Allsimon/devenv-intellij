package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.affogato.lsp.Analysis;
import org.jetbrains.annotations.Nullable;

/**
 * Where a nixpkgs attribute or an option is declared, as the evaluator found it, and the targets the PSI does not
 * resolve: attributes of local sets and of {@code with}s of them, and attributes of files selected through
 * {@code import}.
 */
public final class NixGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement source, int offset, Editor editor) {
        PsiFile file = source == null ? null : source.getContainingFile();
        if (!(file instanceof NixFile)) {
            return null;
        }
        PsiReference reference = file.findReferenceAt(offset);
        if (reference != null && reference.resolve() != null) {
            return null;
        }
        Analysis analysis = NixAnalyses.of(file);
        Analysis.Location location = analysis.declaration(offset);
        if (location != null) {
            PsiElement target = NixAnalyses.at(file.getProject(), location.path(), location.line(), location.column());
            return target == null ? null : new PsiElement[] {target};
        }
        Analysis.Span span = analysis.definition(offset);
        PsiFile in = span == null ? null : span.path() == null ? file : NixAnalyses.file(file.getProject(), span.path());
        if (in == null) {
            return null;
        }
        PsiElement target = span.start() == 0 && span.end() == 0 ? in : in.findElementAt(span.start());
        return target == null ? null : new PsiElement[] {target};
    }
}
