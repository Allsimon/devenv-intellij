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
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Moves the selected expression into a binding: of the enclosing {@code let} where its variables keep their meaning,
 * else of a new {@code let} around the largest expression where they do.
 */
public final class NixExtractBindingIntention extends PsiElementBaseIntentionAction {
    private static final Set<NodeKind> NOT_EXPRESSIONS = Set.of(NodeKind.ATTR_PATH, NodeKind.ATTR_NAME,
            NodeKind.DYNAMIC_ATTR, NodeKind.BINDING, NodeKind.INHERIT, NodeKind.FORMALS, NodeKind.FORMAL,
            NodeKind.PARAM, NodeKind.INTERPOLATION);

    @Override
    public @NotNull String getFamilyName() {
        return "Extract into a let binding";
    }

    @Override
    public @NotNull String getText() {
        return getFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return expression(editor, element.getContainingFile()) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiElement expression = expression(editor, element.getContainingFile());
        if (expression == null) {
            return;
        }
        Document document = editor.getDocument();
        String value = expression.getText();
        TextRange range = expression.getTextRange();
        ASTNode let = enclosingLet(expression);
        String name = unique(suggest(expression), let != null ? let.getPsi() : expression);
        if (let != null) {
            ASTNode in = let.findChildByType(NixElementTypes.of(Kind.IN));
            int lineStart = document.getLineStartOffset(document.getLineNumber(in.getStartOffset()));
            ASTNode last = NixFormals.children(let).stream()
                    .filter(c -> NixFormals.is(c, NodeKind.BINDING) || NixFormals.is(c, NodeKind.INHERIT))
                    .reduce((a, b) -> b).orElseThrow();
            boolean ownLine = document.getText(new TextRange(lineStart, in.getStartOffset())).isBlank()
                    && document.getLineNumber(last.getStartOffset()) != document.getLineNumber(let.getStartOffset());
            document.replaceString(range.getStartOffset(), range.getEndOffset(), name);
            if (ownLine) {
                int lastLine = document.getLineStartOffset(document.getLineNumber(last.getStartOffset()));
                String indent = document.getText(new TextRange(lastLine, last.getStartOffset()));
                document.insertString(lineStart, indent + name + " = " + value + ";\n");
            } else {
                document.insertString(in.getStartOffset(), name + " = " + value + "; ");
            }
        } else {
            PsiElement around = wrapped(expression);
            TextRange outer = around.getTextRange();
            String text = document.getText(outer);
            int at = range.getStartOffset() - outer.getStartOffset();
            String body = text.substring(0, at) + name + text.substring(at + value.length());
            document.replaceString(outer.getStartOffset(), outer.getEndOffset(),
                    "let " + name + " = " + value + "; in " + body);
        }
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    /** The expression the selection covers exactly, whitespace aside. */
    private static @Nullable PsiElement expression(Editor editor, PsiFile file) {
        if (!(file instanceof NixFile) || !editor.getSelectionModel().hasSelection()) {
            return null;
        }
        String text = file.getText();
        int start = editor.getSelectionModel().getSelectionStart();
        int end = editor.getSelectionModel().getSelectionEnd();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return null;
        }
        PsiElement element = PsiTreeUtil.findCommonParent(file.findElementAt(start), file.findElementAt(end - 1));
        while (element != null && !(element instanceof PsiFile) && element.getTextRange().getStartOffset() == start
                && element.getTextRange().getEndOffset() == end) {
            ASTNode node = element.getNode();
            boolean expression = NixFormals.isExpression(node)
                    && NOT_EXPRESSIONS.stream().noneMatch(k -> NixFormals.is(node, k));
            if (expression && (element.getParent() == null
                    || !element.getParent().getTextRange().equals(element.getTextRange()))) {
                return element;
            }
            element = element.getParent();
        }
        return null;
    }

    /** The innermost {@code let} above the expression where each of its variables means the same. */
    private static @Nullable ASTNode enclosingLet(PsiElement expression) {
        for (PsiElement parent = expression.getParent(); parent != null && !(parent instanceof PsiFile);
             parent = parent.getParent()) {
            if (NixFormals.is(parent.getNode(), NodeKind.LET)) {
                return keepsMeaning(expression, parent) ? parent.getNode() : null;
            }
        }
        return null;
    }

    /**
     * The largest expression around {@code expression} where its variables keep their meaning, without crossing a
     * lambda or a {@code let}: a function stays a function, and a {@code let} that did not fit is not put out of
     * reach.
     */
    private static PsiElement wrapped(PsiElement expression) {
        PsiElement around = expression;
        for (PsiElement parent = expression.getParent(); parent != null && !(parent instanceof PsiFile);
             parent = parent.getParent()) {
            ASTNode node = parent.getNode();
            if (NixFormals.is(node, NodeKind.LAMBDA) || NixFormals.is(node, NodeKind.LET)
                    || !keepsMeaning(expression, parent)) {
                break;
            }
            if (NixFormals.isExpression(node) && NOT_EXPRESSIONS.stream().noneMatch(k -> NixFormals.is(node, k))) {
                around = parent;
            }
        }
        return around;
    }

    /** Whether the expression's variables resolve the same seen from {@code scope} as where they are. */
    private static boolean keepsMeaning(PsiElement expression, PsiElement scope) {
        for (NixVarElement var : PsiTreeUtil.findChildrenOfType(expression, NixVarElement.class)) {
            if (NixScopes.resolve(var.getText(), scope) != var.getReference().resolve()) {
                return false;
            }
        }
        return !(expression instanceof NixVarElement var)
                || NixScopes.resolve(var.getText(), scope) == var.getReference().resolve();
    }

    private static String suggest(PsiElement expression) {
        ASTNode node = expression.getNode();
        if (NixFormals.is(node, NodeKind.SELECT)) {
            ASTNode path = node.findChildByType(NixElementTypes.of(NodeKind.ATTR_PATH));
            ASTNode last = path == null ? null : path.getLastChildNode();
            if (NixFormals.is(last, NodeKind.ATTR_NAME)) {
                return last.getText();
            }
        }
        if (NixFormals.is(node, NodeKind.APPLY)) {
            return suggest(node.getFirstChildNode().getPsi());
        }
        if (NixFormals.is(node, NodeKind.VAR)) {
            return node.getText();
        }
        return NixFormals.is(node, NodeKind.STRING) || NixFormals.is(node, NodeKind.IND_STRING) ? "text" : "value";
    }

    private static String unique(String name, PsiElement at) {
        Set<String> taken = NixScopes.visible(at).stream().map(NixNamedElement::getName).collect(Collectors.toSet());
        if (NixFormals.is(at.getNode(), NodeKind.LET)) {
            NixFormals.children(at.getNode()).stream()
                    .filter(c -> NixFormals.is(c, NodeKind.BINDING))
                    .map(c -> c.findChildByType(NixElementTypes.of(NodeKind.ATTR_PATH)))
                    .filter(p -> p != null)
                    .forEach(p -> taken.add(p.getFirstChildNode().getText()));
        }
        String candidate = name;
        for (int i = 2; taken.contains(candidate); i++) {
            candidate = name + i;
        }
        return candidate;
    }
}
