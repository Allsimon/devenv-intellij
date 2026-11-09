package com.allsimon.intellij.nixlang;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.affogato.parser.Lexeme.Kind;
import org.jetbrains.annotations.NotNull;

/** Checks comments and string text, but not a string another language is injected into, such as a phase's Bash. */
public final class NixSpellcheckingStrategy extends SpellcheckingStrategy {
    @Override
    public @NotNull Tokenizer<?> getTokenizer(PsiElement element) {
        if (NixElementTypes.kind(element.getNode().getElementType()) == Kind.STR_TEXT) {
            PsiElement string = element.getParent();
            boolean injected = string instanceof NixStringElement host
                    && InjectedLanguageManager.getInstance(element.getProject()).getInjectedPsiFiles(host) != null;
            return injected ? EMPTY_TOKENIZER : TEXT_TOKENIZER;
        }
        return super.getTokenizer(element);
    }
}
