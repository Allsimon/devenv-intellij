package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.parser.Lexeme.Kind;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Replaces the uses of a {@code let} binding with its value, then removes the binding. */
public final class NixInlineBindingIntention extends PsiElementBaseIntentionAction {
    /** Values that need no parentheses wherever they go. */
    private static final Set<NodeKind> SIMPLE = Set.of(NodeKind.VAR, NodeKind.INT, NodeKind.FLOAT, NodeKind.URI,
            NodeKind.STRING, NodeKind.IND_STRING, NodeKind.PATH, NodeKind.SEARCH_PATH, NodeKind.PAREN, NodeKind.LIST,
            NodeKind.ATTR_SET, NodeKind.SELECT);

    private record Plan(ASTNode let, ASTNode binding, String value, List<PsiElement> uses) {}

    @Override
    public @NotNull String getFamilyName() {
        return "Inline binding";
    }

    @Override
    public @NotNull String getText() {
        return getFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return plan(element) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Plan plan = plan(element);
        if (plan == null) {
            return;
        }
        Document document = editor.getDocument();
        record Edit(TextRange range, String text) {}
        List<Edit> edits = new ArrayList<>();
        for (PsiElement use : plan.uses()) {
            edits.add(new Edit(use.getTextRange(), plan.value()));
        }
        List<ASTNode> bindings = NixFormals.children(plan.let()).stream()
                .filter(c -> NixFormals.is(c, NodeKind.BINDING) || NixFormals.is(c, NodeKind.INHERIT))
                .toList();
        if (bindings.size() == 1) {
            ASTNode body = NixFormals.children(plan.let()).getLast();
            edits.add(new Edit(new TextRange(plan.let().getStartOffset(), body.getStartOffset()), ""));
        } else {
            TextRange binding = plan.binding().getTextRange();
            int end = binding.getEndOffset();
            CharSequence text = document.getCharsSequence();
            while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
                end++;
            }
            if (end < text.length() && text.charAt(end) == '\n') {
                int start = binding.getStartOffset();
                while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
                    start--;
                }
                edits.add(new Edit(new TextRange(start, end + 1), ""));
            } else {
                edits.add(new Edit(new TextRange(binding.getStartOffset(), end), ""));
            }
        }
        edits.sort(Comparator.comparingInt((Edit e) -> e.range().getStartOffset()).reversed());
        for (Edit edit : edits) {
            document.replaceString(edit.range().getStartOffset(), edit.range().getEndOffset(), edit.text());
        }
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    private static @Nullable Plan plan(PsiElement element) {
        if (element instanceof PsiWhiteSpace) {
            element = PsiTreeUtil.prevLeaf(element);
        }
        NixNamedElement name = PsiTreeUtil.getParentOfType(element, NixNamedElement.class, false);
        ASTNode path = name == null ? null : name.getNode().getTreeParent();
        ASTNode binding = path == null ? null : path.getTreeParent();
        ASTNode let = binding == null ? null : binding.getTreeParent();
        if (!NixFormals.is(path, NodeKind.ATTR_PATH) || NixFormals.children(path).size() != 1
                || !NixFormals.is(binding, NodeKind.BINDING) || !NixFormals.is(let, NodeKind.LET)) {
            return null;
        }
        List<ASTNode> parts = NixFormals.children(binding);
        int assign = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (NixElementTypes.kind(parts.get(i).getElementType()) == Kind.ASSIGN) {
                assign = i;
            }
        }
        if (assign < 0 || assign + 1 >= parts.size()) {
            return null;
        }
        PsiElement value = parts.get(assign + 1).getPsi();
        List<PsiElement> uses = new ArrayList<>();
        for (var reference : ReferencesSearch.search(name, new LocalSearchScope(let.getPsi())).findAll()) {
            PsiElement use = reference.getElement();
            if (!(use instanceof NixVarElement) || PsiTreeUtil.isAncestor(value, use, false)) {
                return null;
            }
            uses.add(use);
        }
        if (uses.isEmpty() || !sameMeaningAt(value, uses)) {
            return null;
        }
        String text = value.getText();
        boolean simple = SIMPLE.stream().anyMatch(kind -> NixFormals.is(value.getNode(), kind));
        return new Plan(let, binding, simple ? text : "(" + text + ")", uses);
    }

    /** Whether each variable of the value resolves to the same definition at every use. */
    private static boolean sameMeaningAt(PsiElement value, List<PsiElement> uses) {
        for (NixVarElement var : PsiTreeUtil.findChildrenOfType(value, NixVarElement.class)) {
            PsiElement here = var.getReference().resolve();
            for (PsiElement use : uses) {
                if (NixScopes.resolve(var.getText(), use) != here) {
                    return false;
                }
            }
        }
        if (value instanceof NixVarElement var) {
            PsiElement here = var.getReference().resolve();
            return uses.stream().allMatch(use -> NixScopes.resolve(var.getText(), use) == here);
        }
        return true;
    }
}
