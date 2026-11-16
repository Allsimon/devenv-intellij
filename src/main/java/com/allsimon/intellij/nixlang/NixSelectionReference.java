package com.allsimon.intellij.nixlang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.affogato.lsp.Analysis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the attribute a selection picks, as {@code a} in {@code x.a}, to where affogato finds it bound: in a local
 * set, or in the set of a file {@code x} imports. Soft, as most selections reach into what only evaluation knows.
 */
final class NixSelectionReference extends PsiReferenceBase<NixNamedElement> {
    private static final ResolveCache.AbstractResolver<NixSelectionReference, PsiElement> RESOLVER =
            (reference, incompleteCode) -> reference.target();

    NixSelectionReference(@NotNull NixNamedElement element) {
        super(element, TextRange.from(0, element.getTextLength()), true);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, RESOLVER, false, false);
    }

    private @Nullable PsiElement target() {
        NixNamedElement element = getElement();
        PsiFile file = element.getContainingFile();
        Analysis.Span span = NixAnalyses.syntax(file).definition(element.getTextOffset());
        if (span == null || span.start() == span.end()) {
            return null;
        }
        PsiFile in = span.path() == null ? file : NixAnalyses.file(file.getProject(), span.path());
        PsiElement target = in == null ? null
                : PsiTreeUtil.getParentOfType(in.findElementAt(span.start()), NixNamedElement.class, false);
        return target == element ? null : target;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        NixVarElement.replaceIdentifier(getElement(), newElementName);
        return getElement();
    }
}
