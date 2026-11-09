package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.parser.Highlighter;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Adds an undefined variable to the formals of the nearest function that has some. */
public final class NixAddFormalIntention extends PsiElementBaseIntentionAction {
    @Override
    public @NotNull String getFamilyName() {
        return "Add to the function's arguments";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        NixVarElement var = PsiTreeUtil.getParentOfType(element, NixVarElement.class, false);
        if (var == null || formals(var) == null) {
            return false;
        }
        setText("Add '" + var.getText() + "' to the function's arguments");
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        NixVarElement var = PsiTreeUtil.getParentOfType(element, NixVarElement.class, false);
        ASTNode formals = var == null ? null : formals(var);
        if (formals == null) {
            return;
        }
        Document document = editor.getDocument();
        String name = var.getText();
        List<ASTNode> parts = NixFormals.children(formals);
        ASTNode open = parts.getFirst();
        ASTNode close = parts.getLast();
        ASTNode ellipsis = parts.stream()
                .filter(p -> NixElementTypes.kind(p.getElementType()) == Kind.ELLIPSIS).findFirst().orElse(null);
        List<ASTNode> named = parts.stream().filter(p -> NixFormals.is(p, NodeKind.FORMAL)).toList();
        boolean multiline = document.getLineNumber(open.getStartOffset()) != document.getLineNumber(close.getStartOffset());
        if (multiline) {
            ASTNode next = ellipsis != null ? ellipsis : close;
            int line = document.getLineStartOffset(document.getLineNumber(next.getStartOffset()));
            ASTNode model = !named.isEmpty() ? named.getFirst() : ellipsis;
            String indent = model == null ? "  " : document.getText(new TextRange(
                    document.getLineStartOffset(document.getLineNumber(model.getStartOffset())), model.getStartOffset()));
            document.insertString(line, indent + name + ",\n");
        } else if (ellipsis != null) {
            document.insertString(ellipsis.getStartOffset(), name + ", ");
        } else if (!named.isEmpty()) {
            ASTNode last = named.getLast();
            document.insertString(last.getStartOffset() + last.getTextLength(), ", " + name);
        } else {
            document.replaceString(open.getStartOffset(), close.getStartOffset() + close.getTextLength(),
                    "{ " + name + " }");
        }
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    /** Whether {@code child} is the body of {@code with}, where its names may come from, rather than its set. */
    private static boolean isBody(ASTNode with, ASTNode child) {
        List<ASTNode> parts = NixFormals.children(with).stream().filter(NixFormals::isExpression).toList();
        return !parts.isEmpty() && parts.getFirst() != child;
    }

    /** The formals the undefined {@code var} can be added to, or null when it is defined or may come from a {@code with}. */
    private static @Nullable ASTNode formals(NixVarElement var) {
        String name = var.getText();
        if (var.getReference().resolve() != null || Highlighter.nameKind(name) != null || name.equals("or")) {
            return null;
        }
        PsiElement child = var;
        for (PsiElement parent = var.getParent(); parent != null && !(parent instanceof PsiFile);
             child = parent, parent = parent.getParent()) {
            if (NixFormals.is(parent.getNode(), NodeKind.WITH) && isBody(parent.getNode(), child.getNode())) {
                return null;
            }
            if (NixFormals.is(parent.getNode(), NodeKind.LAMBDA)) {
                ASTNode formals = parent.getNode().findChildByType(NixElementTypes.of(NodeKind.FORMALS));
                if (formals != null) {
                    return formals;
                }
            }
        }
        return null;
    }
}
