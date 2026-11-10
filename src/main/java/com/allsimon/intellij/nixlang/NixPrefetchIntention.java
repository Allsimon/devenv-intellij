package com.allsimon.intellij.nixlang;

import com.allsimon.intellij.core.DevenvCli;
import com.google.gson.JsonParser;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Asks Nix for the hash of what a {@code fetchurl} or {@code fetchFromGitHub} call fetches, and writes it. */
public final class NixPrefetchIntention extends PsiElementBaseIntentionAction {
    /** A fetcher call: its set of arguments, and the command that prefetches what they name. */
    record Fetch(ASTNode set, List<String> command) {}

    @Override
    public @NotNull String getFamilyName() {
        return "Prefetch the hash";
    }

    @Override
    public @NotNull String getText() {
        return getFamilyName();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    /** None: it would run Nix, and fetch, to show it. */
    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor,
                                                         @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return fetch(element) != null;
    }

    /**
     * Prefetches in the background, then writes the hash in a command of its own: an intention that does not start
     * in a write action cannot change the document while it runs.
     */
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Fetch fetch = fetch(element);
        PsiFile file = element.getContainingFile();
        if (fetch == null || !FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        Document document = editor.getDocument();
        var set = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(fetch.set().getPsi());
        new Task.Backgroundable(project, "Prefetching", true) {
            private String hash;
            private String error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    hash = prefetch(fetch.command());
                } catch (ExecutionException e) {
                    error = e.getMessage();
                }
            }

            @Override
            public void onFinished() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    PsiElement current = set.getElement();
                    if (hash != null && current != null
                            && FileModificationService.getInstance().prepareFileForWrite(file)) {
                        WriteCommandAction.runWriteCommandAction(project, "Prefetch Hash", null,
                                () -> write(project, document, current.getNode(), hash));
                    } else if (!editor.isDisposed()) {
                        HintManager.getInstance().showErrorHint(editor, error == null ? "Prefetching failed" : error);
                    }
                });
            }
        }.queue();
    }

    /** The fetcher call {@code element} is in, when its arguments name what it fetches. */
    static @Nullable Fetch fetch(PsiElement element) {
        for (PsiElement e = element; e != null; e = e.getParent()) {
            if (!NixFormals.is(e.getNode(), NodeKind.APPLY)) {
                continue;
            }
            List<ASTNode> parts = NixFormals.children(e.getNode());
            if (parts.size() != 2 || !NixFormals.is(parts.get(1), NodeKind.ATTR_SET)) {
                continue;
            }
            String function = parts.getFirst().getText();
            Map<String, String> arguments = arguments(parts.get(1));
            if (function.endsWith("fetchurl") && arguments.get("url") != null) {
                return new Fetch(parts.get(1), List.of("store", "prefetch-file", "--json", arguments.get("url")));
            }
            String rev = arguments.getOrDefault("rev", arguments.get("tag"));
            if (function.endsWith("fetchFromGitHub") && arguments.get("owner") != null
                    && arguments.get("repo") != null && rev != null) {
                return new Fetch(parts.get(1), List.of("flake", "prefetch", "--json",
                        "github:" + arguments.get("owner") + "/" + arguments.get("repo") + "/" + rev));
            }
        }
        return null;
    }

    /** The bindings of a set whose value is a string without interpolation. */
    private static Map<String, String> arguments(ASTNode set) {
        Map<String, String> arguments = new HashMap<>();
        for (ASTNode binding : NixFormals.children(set)) {
            ASTNode path = binding.findChildByType(NixElementTypes.of(NodeKind.ATTR_PATH));
            List<ASTNode> parts = NixFormals.is(binding, NodeKind.BINDING) ? NixFormals.children(binding) : List.of();
            if (path == null || parts.size() < 3 || !NixFormals.is(parts.get(2), NodeKind.STRING)) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            boolean constant = true;
            for (ASTNode child = parts.get(2).getFirstChildNode(); child != null; child = child.getTreeNext()) {
                Kind kind = NixElementTypes.kind(child.getElementType());
                if (kind == Kind.STR_TEXT) {
                    text.append(child.getText());
                } else if (kind != Kind.STR_OPEN && kind != Kind.STR_CLOSE) {
                    constant = false;
                }
            }
            if (constant) {
                arguments.put(path.getText(), text.toString());
            }
        }
        return arguments;
    }

    static String prefetch(List<String> arguments) throws ExecutionException {
        File nix = PathEnvironmentVariableUtil.findInPath("nix");
        if (nix == null) {
            throw new ExecutionException("'nix' is not on the PATH");
        }
        GeneralCommandLine command = DevenvCli.commandLine(nix, System.getProperty("java.io.tmpdir"),
                arguments.toArray(String[]::new));
        ProcessOutput output = DevenvCli.run(command, 300_000);
        if (output.getExitCode() != 0 || output.isTimeout()) {
            throw new ExecutionException(DevenvCli.stripAnsi(output.getStderr()).strip());
        }
        return JsonParser.parseString(output.getStdout()).getAsJsonObject().get("hash").getAsString();
    }

    /** Writes {@code hash = "<hash>";} in the set, over its {@code hash} or {@code sha256}, else after its last binding. */
    private static void write(Project project, Document document, ASTNode set, String hash) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        ASTNode existing = null;
        ASTNode last = null;
        for (ASTNode binding : NixFormals.children(set)) {
            if (NixFormals.is(binding, NodeKind.BINDING)) {
                ASTNode path = binding.findChildByType(NixElementTypes.of(NodeKind.ATTR_PATH));
                if (path != null && List.of("hash", "sha256").contains(path.getText())) {
                    existing = binding;
                }
                last = binding;
            }
        }
        String text = "hash = \"" + hash + "\";";
        if (existing != null) {
            document.replaceString(existing.getStartOffset(), existing.getStartOffset() + existing.getTextLength(), text);
        } else if (last != null) {
            int end = last.getStartOffset() + last.getTextLength();
            int line = document.getLineNumber(last.getStartOffset());
            String indent = document.getText(new com.intellij.openapi.util.TextRange(
                    document.getLineStartOffset(line), last.getStartOffset()));
            document.insertString(end, indent.isBlank() && document.getLineNumber(set.getStartOffset()) != line
                    ? "\n" + indent + text : " " + text);
        }
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
