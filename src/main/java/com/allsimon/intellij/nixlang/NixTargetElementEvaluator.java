package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/** Makes find usages and rename on {@code a} in {@code x.a} act on the attribute it selects, not on the selection. */
public final class NixTargetElementEvaluator extends TargetElementEvaluatorEx2 {
    @Override
    public boolean isAcceptableNamedParent(@NotNull PsiElement parent) {
        return !(parent instanceof NixNamedElement named && named.isSelected());
    }
}
