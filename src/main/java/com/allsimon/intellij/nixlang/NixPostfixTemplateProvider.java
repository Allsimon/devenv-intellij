package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Postfix templates such as {@code x.mkIf}: the key typed after a dot rewrites the expression before it, the
 * innermost first when several end there.
 */
public final class NixPostfixTemplateProvider implements PostfixTemplateProvider {
    private static final Set<NodeKind> SIMPLE = Set.of(NodeKind.VAR, NodeKind.INT, NodeKind.FLOAT, NodeKind.URI,
            NodeKind.STRING, NodeKind.IND_STRING, NodeKind.PATH, NodeKind.SEARCH_PATH, NodeKind.PAREN, NodeKind.LIST,
            NodeKind.ATTR_SET, NodeKind.SELECT);

    private final Set<PostfixTemplate> templates = Set.of(
            new NixPostfixTemplate("mkIf", "lib.mkIf cfg.enable expr", "lib.mkIf $cond$ $expr$$END$", true, this),
            new NixPostfixTemplate("list", "[ expr ]", "[ $expr$ ]$END$", false, this),
            new NixPostfixTemplate("paren", "(expr)", "($expr$)$END$", false, this),
            new NixPostfixTemplate("not", "!expr", "!$expr$$END$", true, this),
            new NixPostfixTemplate("toString", "toString expr", "toString $expr$$END$", true, this));

    @Override
    public @NotNull String getId() {
        return "nix";
    }

    @Override
    public @NotNull String getPresentableName() {
        return "Nix";
    }

    @Override
    public @NotNull Set<PostfixTemplate> getTemplates() {
        return templates;
    }

    @Override
    public boolean isTerminalSymbol(char currentChar) {
        return currentChar == '.';
    }

    @Override
    public void preExpand(@NotNull PsiFile file, @NotNull Editor editor) {
    }

    @Override
    public void afterExpand(@NotNull PsiFile file, @NotNull Editor editor) {
    }

    @Override
    public @NotNull PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset) {
        return copyFile;
    }

    /** The text of an expression, in parentheses where an argument needs them. */
    private static String expression(PsiElement element, boolean argument) {
        boolean simple = SIMPLE.stream().anyMatch(kind -> NixFormals.is(element.getNode(), kind));
        return argument && !simple ? "(" + element.getText() + ")" : element.getText();
    }

    private static final class NixPostfixTemplate extends StringBasedPostfixTemplate {
        private final String template;
        private final boolean argument;

        NixPostfixTemplate(String name, String example, String template, boolean argument, PostfixTemplateProvider provider) {
            super(name, example, new Selector(), provider);
            this.template = template;
            this.argument = argument;
        }

        /** In parentheses where an application or a negation would bind with what is around it. */
        @Override
        public String getTemplateString(@NotNull PsiElement element) {
            var parent = element.getParent() == null ? null : element.getParent().getNode();
            boolean operand = Set.of(NodeKind.APPLY, NodeKind.BINARY, NodeKind.NOT, NodeKind.NEGATE, NodeKind.SELECT,
                    NodeKind.HAS_ATTR).stream().anyMatch(kind -> NixFormals.is(parent, kind));
            return argument && operand ? "(" + template.replace("$END$", "") + ")$END$" : template;
        }

        /** The expression only: the default, its parent, would take the binding around it. */
        @Override
        protected PsiElement getElementToRemove(PsiElement expr) {
            return expr;
        }

        @Override
        public void setVariables(@NotNull com.intellij.codeInsight.template.Template result, @NotNull PsiElement element) {
            result.addVariable("expr", new TextExpression(expression(element, argument)), false);
            if (template.contains("$cond$")) {
                String condition = NixScopes.resolve("cfg", element) != null ? "cfg.enable" : "condition";
                result.addVariable("cond", new TextExpression(condition), true);
            }
        }
    }

    /** The expressions ending where the key and its dot were, innermost first. */
    private static final class Selector extends PostfixTemplateExpressionSelectorBase {
        private static final Set<NodeKind> NOT_EXPRESSIONS = Set.of(NodeKind.ATTR_PATH, NodeKind.ATTR_NAME,
                NodeKind.DYNAMIC_ATTR, NodeKind.BINDING, NodeKind.INHERIT, NodeKind.FORMALS, NodeKind.FORMAL,
                NodeKind.PARAM, NodeKind.INTERPOLATION);

        Selector() {
            super(null);
        }

        @Override
        protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document,
                                                             int offset) {
            List<PsiElement> expressions = new ArrayList<>();
            for (PsiElement e = context.getContainingFile().findElementAt(offset - 1); e != null && !(e instanceof PsiFile);
                 e = e.getParent()) {
                if (e.getTextRange().getEndOffset() != offset) {
                    break;
                }
                var node = e.getNode();
                if (NixFormals.isExpression(node) && NOT_EXPRESSIONS.stream().noneMatch(k -> NixFormals.is(node, k))) {
                    expressions.add(e);
                }
            }
            return expressions;
        }
    }
}
