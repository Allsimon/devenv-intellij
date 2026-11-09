package com.allsimon.intellij.nixlang;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;

/** A {@code "..."} or {@code ''...''} string, which another language can be injected into. */
public final class NixStringElement extends ASTWrapperPsiElement implements PsiLanguageInjectionHost {
    NixStringElement(@NotNull ASTNode node) {
        super(node);
    }

    public boolean isIndented() {
        return getNode().getElementType() == NixElementTypes.of(NodeKind.IND_STRING);
    }

    /** The range between the quotes, relative to this element. */
    public @NotNull TextRange contentRange() {
        ASTNode open = getNode().getFirstChildNode();
        ASTNode close = getNode().getLastChildNode();
        int end = isClosed() ? close.getStartOffsetInParent() : getTextLength();
        return new TextRange(open.getTextLength(), Math.max(open.getTextLength(), end));
    }

    private boolean isClosed() {
        ASTNode close = getNode().getLastChildNode();
        Kind kind = NixElementTypes.kind(close.getElementType());
        return close != getNode().getFirstChildNode() && (kind == Kind.STR_CLOSE || kind == Kind.IND_STR_CLOSE);
    }

    @Override
    public boolean isValidHost() {
        return isClosed();
    }

    @Override
    public PsiLanguageInjectionHost updateText(@NotNull String text) {
        var file = PsiFileFactory.getInstance(getProject()).createFileFromText("dummy.nix", NixFileType.INSTANCE, text);
        NixStringElement string = PsiTreeUtil.findChildOfType(file, NixStringElement.class);
        return string == null ? this : (PsiLanguageInjectionHost) replace(string);
    }

    @Override
    public @NotNull LiteralTextEscaper<NixStringElement> createLiteralTextEscaper() {
        return new NixStringEscaper(this);
    }
}
