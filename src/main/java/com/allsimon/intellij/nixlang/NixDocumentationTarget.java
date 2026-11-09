package com.allsimon.intellij.nixlang;

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import org.affogato.lsp.Evaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** What affogato's evaluator says of a nixpkgs attribute or an option, written in Markdown. */
final class NixDocumentationTarget implements DocumentationTarget {
    private final Project project;
    private final String name;
    private final String markdown;

    NixDocumentationTarget(@NotNull Project project, @NotNull String name, @NotNull String markdown) {
        this.project = project;
        this.name = name;
        this.markdown = markdown;
    }

    /** The documentation of a completion: its version or type, and its description or doc comment. */
    static @Nullable NixDocumentationTarget of(@NotNull Project project, @NotNull String name, @Nullable Evaluator.Attribute a) {
        if (a == null || a.type() == null) {
            return null;
        }
        StringBuilder text = new StringBuilder("`").append(name).append("` ").append(NixCompletionContributor.detail(a));
        String documentation = a.description() != null ? a.description() : a.doc();
        if (documentation != null) {
            text.append("\n\n").append(documentation);
        }
        return new NixDocumentationTarget(project, name, text.toString());
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(name).presentation();
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(DocMarkdownToHtmlConverter.convert(project, markdown));
    }
}
