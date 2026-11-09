package com.allsimon.intellij.nixlang;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.jetbrains.annotations.NotNull;

public final class NixBreadcrumbsProvider implements BreadcrumbsProvider {
    @Override
    public Language[] getLanguages() {
        return new Language[]{NixLanguage.INSTANCE};
    }

    @Override
    public boolean acceptElement(@NotNull PsiElement element) {
        return NixBindings.isBinding(element);
    }

    @Override
    public @NotNull String getElementInfo(@NotNull PsiElement element) {
        return NixBindings.name(element);
    }
}
