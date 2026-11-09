package com.allsimon.intellij.nixlint;

import com.allsimon.intellij.core.MyMessageBundle;
import com.allsimon.intellij.nixlang.NixFile;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.lint.Diagnostic;
import org.affogato.lint.Edit;
import org.affogato.lint.Linter;
import org.affogato.lint.Rule;
import org.affogato.lint.Severity;
import org.affogato.lint.Suppressions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registered for every language rather than this plugin's Nix, since a {@code .nix} file may be text or belong to
 * another plugin's Nix language.
 */
public final class AffogatoLintInspection extends LocalInspectionTool {
    private static final String RULE_PREFIX = "rule";

    /** Comma-separated ids of the rules switched off. */
    public String disabledRules = "";

    @Override
    public boolean isAvailableForFile(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        // An injected fragment, such as the Bash of a phase, is named after its host.
        return virtualFile != null && "nix".equals(virtualFile.getExtension())
                && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
        return OptPane.pane(OptPane.checkboxPanel(Arrays.stream(Rule.values())
                .map(rule -> OptPane.checkbox(RULE_PREFIX + "." + rule.id(), rule.id()))
                .toArray(OptCheckbox[]::new)));
    }

    @Override
    public @NotNull OptionController getOptionController() {
        return super.getOptionController().onPrefix(RULE_PREFIX,
                id -> !disabled().contains(id),
                (id, enabled) -> {
                    Set<String> disabled = new LinkedHashSet<>(disabled());
                    if ((Boolean) enabled) {
                        disabled.remove(id);
                    } else {
                        disabled.add(id);
                    }
                    disabledRules = String.join(",", disabled);
                });
    }

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
                                                   boolean isOnTheFly) {
        String text = file.getText();
        if (text.isEmpty()) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        Set<Rule> rules = enabledRules();
        // The parser already marks its own errors; the linter's syntax error then adds nothing.
        if (file instanceof NixFile && PsiTreeUtil.hasErrorElements(file)) {
            rules.remove(Rule.SYNTAX_ERROR);
        }
        return Linter.lint(text, rules).stream()
                // The linter reports a syntax error whatever the rules.
                .filter(diagnostic -> rules.contains(diagnostic.rule()))
                .map(diagnostic -> describe(file, manager, isOnTheFly, text, diagnostic))
                .toArray(ProblemDescriptor[]::new);
    }

    private static ProblemDescriptor describe(PsiFile file, InspectionManager manager, boolean isOnTheFly, String text,
                                              Diagnostic diagnostic) {
        List<LocalQuickFix> fixes = new ArrayList<>();
        if (!diagnostic.fix().isEmpty()) {
            fixes.add(new LintFix(diagnostic.rule(), diagnostic.start(),
                    text.substring(diagnostic.start(), diagnostic.end())));
        }
        if (diagnostic.rule() != Rule.SYNTAX_ERROR) {
            fixes.add(new SuppressFix(diagnostic.rule(), false));
            fixes.add(new SuppressFix(diagnostic.rule(), true));
        }
        return manager.createProblemDescriptor(file, range(text, diagnostic),
                StringUtil.escapeXmlEntities(diagnostic.message() + " (" + diagnostic.rule().id() + ")"),
                highlightType(diagnostic), isOnTheFly, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }

    /** The finding's range, widened to a character when empty, which the editor would not show. */
    private static TextRange range(String text, Diagnostic diagnostic) {
        int start = diagnostic.start();
        int end = diagnostic.end();
        if (start == end) {
            if (end < text.length()) {
                end++;
            } else {
                start--;
            }
        }
        return new TextRange(start, end);
    }

    private static ProblemHighlightType highlightType(Diagnostic diagnostic) {
        if (diagnostic.severity() == Severity.ERROR) {
            return ProblemHighlightType.GENERIC_ERROR;
        }
        if (diagnostic.severity() == Severity.HINT) {
            return ProblemHighlightType.WEAK_WARNING;
        }
        return diagnostic.rule().name().startsWith("UNUSED_")
                ? ProblemHighlightType.LIKE_UNUSED_SYMBOL
                : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }

    private Set<String> disabled() {
        return Arrays.stream(disabledRules.split(","))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Rule> enabledRules() {
        Set<String> disabled = disabled();
        Set<Rule> rules = EnumSet.noneOf(Rule.class);
        for (Rule rule : Rule.values()) {
            if (!disabled.contains(rule.id())) {
                rules.add(rule);
            }
        }
        return rules;
    }

    /**
     * Lints the document again when applied, since earlier fixes may have moved the finding, and applies the fix of
     * the finding of the same rule over the same text nearest to where it was found.
     */
    private record LintFix(Rule rule, int start, String original) implements LocalQuickFix {
        @Override
        public @NotNull String getFamilyName() {
            return MyMessageBundle.message("inspection.nixlint.fix", rule.id());
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            if (document == null) {
                return;
            }
            String text = document.getText();
            Linter.lint(text, EnumSet.of(rule)).stream()
                    .filter(diagnostic -> !diagnostic.fix().isEmpty()
                            && text.substring(diagnostic.start(), diagnostic.end()).equals(original))
                    .min(Comparator.comparingInt(diagnostic -> Math.abs(diagnostic.start() - start)))
                    .ifPresent(diagnostic -> apply(document, diagnostic.fix()));
        }

        private static void apply(Document document, List<Edit> edits) {
            edits.stream()
                    .sorted(Comparator.comparingInt(Edit::start).reversed())
                    .forEach(edit -> document.replaceString(edit.start(), edit.end(), edit.replacement()));
        }
    }

    /** Writes the comment that switches the rule off, above the finding's line or atop the file. */
    private record SuppressFix(Rule rule, boolean wholeFile) implements LocalQuickFix {
        @Override
        public @NotNull String getFamilyName() {
            return MyMessageBundle.message(wholeFile ? "inspection.nixlint.suppress.file"
                    : "inspection.nixlint.suppress.line", rule.id());
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            if (document == null) {
                return;
            }
            if (wholeFile) {
                document.insertString(0, Suppressions.ignoreFile(rule) + "\n");
                return;
            }
            int offset = descriptor.getPsiElement().getTextRange().getStartOffset()
                    + descriptor.getTextRangeInElement().getStartOffset();
            int lineNumber = document.getLineNumber(offset);
            int lineStart = document.getLineStartOffset(lineNumber);
            String line = document.getText(new TextRange(lineStart, document.getLineEndOffset(lineNumber)));
            String indent = line.substring(0, line.length() - line.stripLeading().length());
            document.insertString(lineStart, indent + Suppressions.ignore(rule) + "\n");
        }
    }
}
