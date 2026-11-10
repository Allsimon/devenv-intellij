package com.allsimon.intellij.nixlang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.affogato.parser.Injections;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Injects the language affogato's {@link Injections} finds for a string, with the shell rules
 * {@link NixInjectionSettings} sets. A language is resolved through the IDE: one whose plugin is missing is not
 * injected. An interpolation becomes a placeholder, {@code $__nix} in Bash, so that {@code ${pkgs.foo}/bin/foo} reads
 * as a path.
 */
public final class NixInjector implements MultiHostInjector {
    private static final String SHELL = "Shell Script";

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof NixStringElement string) || !string.isValidHost()) {
            return;
        }
        Language language = languageOf(string.getNode());
        if (language == null) {
            return;
        }
        String placeholder = language.getID().equals(SHELL) ? "$__nix" : "__nix";

        List<TextRange> texts = new ArrayList<>();
        List<Integer> interpolationsBefore = new ArrayList<>();
        int interpolations = 0;
        TextRange content = string.contentRange();
        for (ASTNode child = string.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            TextRange range = TextRange.from(child.getStartOffsetInParent(), child.getTextLength());
            if (!content.contains(range)) {
                continue;
            }
            if (child.getElementType() == NixElementTypes.of(NodeKind.INTERPOLATION)) {
                interpolations++;
            } else if (interpolations == 0 && !texts.isEmpty()) {
                texts.set(texts.size() - 1, texts.getLast().union(range));
            } else {
                texts.add(range);
                interpolationsBefore.add(interpolations);
                interpolations = 0;
            }
        }
        if (texts.isEmpty()) {
            return;
        }

        registrar.startInjecting(language);
        for (int i = 0; i < texts.size(); i++) {
            String prefix = i == 0 ? placeholder.repeat(interpolationsBefore.get(0)) : null;
            int after = i + 1 < texts.size() ? interpolationsBefore.get(i + 1) : interpolations;
            registrar.addPlace(prefix, placeholder.repeat(after), string, texts.get(i));
        }
        registrar.doneInjecting();
    }

    private static @Nullable Language languageOf(ASTNode string) {
        Injections.Injection injection = Injections.of(NixScopes.VIEW, string.getPsi(),
                NixInjectionSettings.getInstance().rules());
        if (injection == null) {
            return null;
        }
        return switch (injection.kind()) {
            case SHELL -> Language.findLanguageByID(SHELL);
            case NAMED -> byName(injection.value());
            case FILE -> byFileName(injection.value());
        };
    }

    /** The {@code /* lang *\/} comment right before a string, if any. */
    static @Nullable ASTNode languageComment(ASTNode string) {
        PsiElement previous = string.getPsi().getPrevSibling();
        while (previous instanceof PsiWhiteSpace) {
            previous = previous.getPrevSibling();
        }
        return previous instanceof PsiComment comment && Injections.BLOCK_LANGUAGE.matcher(comment.getText()).matches()
                ? comment.getNode() : null;
    }

    private static @Nullable Language byName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (Set.of("bash", "sh", "shell", "zsh").contains(lower)) {
            return Language.findLanguageByID(SHELL);
        }
        for (Language language : Language.getRegisteredLanguages()) {
            if (language.getID().equalsIgnoreCase(name) || language.getDisplayName().equalsIgnoreCase(name)) {
                return language;
            }
        }
        return byFileName("file." + lower);
    }

    private static @Nullable Language byFileName(String name) {
        if (FileTypeManager.getInstance().getFileTypeByFileName(name) instanceof LanguageFileType type
                && type.getLanguage() != PlainTextLanguage.INSTANCE) {
            return type.getLanguage();
        }
        return null;
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(NixStringElement.class);
    }
}
