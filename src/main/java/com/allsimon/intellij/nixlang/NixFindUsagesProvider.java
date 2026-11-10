package com.allsimon.intellij.nixlang;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;

public final class NixFindUsagesProvider implements FindUsagesProvider {
    @Override
    public WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(new NixLexer(), TokenSet.create(NixElementTypes.of(Kind.ID)),
                NixElementTypes.COMMENTS, TokenSet.orSet(NixElementTypes.STRING_CONTENT, TokenSet.create(
                NixElementTypes.of(Kind.PATH), NixElementTypes.of(Kind.PATH_TEXT))));
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof NixNamedElement;
    }

    @Override
    public String getHelpId(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        var type = element.getNode().getElementType();
        if (type == NixElementTypes.of(NodeKind.PARAM)) {
            return "parameter";
        }
        return type == NixElementTypes.of(NodeKind.FORMAL) ? "formal" : "binding";
    }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        return element instanceof NixNamedElement named && named.getName() != null ? named.getName() : "";
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
