package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Shows what {@code nix eval} gives for the selection, or for the value of the binding at the caret. */
public final class NixEvalAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getData(CommonDataKeys.EDITOR) != null
                && event.getData(CommonDataKeys.PSI_FILE) instanceof NixFile);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        TextRange range = editor == null || file == null ? null : range(editor, file);
        if (range == null || file.getVirtualFile() == null) {
            if (editor != null) {
                HintManager.getInstance().showErrorHint(editor, "Select an expression to evaluate");
            }
            return;
        }
        String expression = NixEval.expression(file, range);
        String directory = file.getVirtualFile().getParent().getPath();
        new Task.Backgroundable(file.getProject(), "Evaluating with nix eval", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                NixEval.Outcome outcome = NixEval.evaluate(expression, directory);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (outcome.ok()) {
                        HintManager.getInstance().showInformationHint(editor, outcome.text());
                    } else {
                        HintManager.getInstance().showErrorHint(editor, outcome.text());
                    }
                });
            }
        }.queue();
    }

    /** The selection, or the value of the binding at the caret. */
    static @Nullable TextRange range(Editor editor, PsiFile file) {
        if (editor.getSelectionModel().hasSelection()) {
            return new TextRange(editor.getSelectionModel().getSelectionStart(),
                    editor.getSelectionModel().getSelectionEnd());
        }
        var element = file.findElementAt(editor.getCaretModel().getOffset());
        ASTNode value = element == null ? null : NixMkIf.valueAt(element);
        return value == null ? null : value.getTextRange();
    }
}
