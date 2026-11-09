package com.allsimon.intellij.nixlang;

import com.allsimon.intellij.nixeval.NixEvaluatorService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.paths.WebReference;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.affogato.lsp.Evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NixAnalysisFeaturesTest extends BasePlatformTestCase {
    private static final Evaluator.Attribute HELLO = new Evaluator.Attribute(
            "derivation", null, "hello-2.12.1", "2.12.1", "A program that produces a familiar, friendly greeting",
            "https://www.gnu.org/software/hello/", null, null, null, null, 0, 0);

    private static final Evaluator.Attribute NONE =
            new Evaluator.Attribute(null, null, null, null, null, null, null, null, null, null, 0, 0);

    /** nixpkgs with hello and hello-unfree in it. */
    private static final class Nixpkgs implements Evaluator {
        @Override
        public List<String> names(List<String> path) {
            return path.isEmpty() ? List.of("hello", "hello-unfree") : List.of();
        }

        @Override
        public List<Attribute> describeAll(List<List<String>> paths) {
            List<Attribute> attributes = new ArrayList<>();
            for (List<String> path : paths) attributes.add(path.equals(List.of("hello")) ? HELLO : NONE);
            return attributes;
        }

        @Override
        public void whenDescribed(List<List<String>> paths, Runnable then) {
            then.run();
        }

        @Override
        public List<String> optionNames(List<String> path) {
            return List.of();
        }

        @Override
        public Attribute describeOption(List<String> path) {
            return NONE;
        }

        @Override
        public void configureOptions(Map<String, String> expressions) {}

        @Override
        public Evaluator project(String key, Map<String, String> options) {
            return this;
        }

        @Override
        public void configure(String expression) {}

        @Override
        public void reset() {}
    }

    private void withNixpkgs() {
        NixEvaluatorService.getInstance(getProject()).replaceEvaluator(new Nixpkgs(), getTestRootDisposable());
    }

    public void testCompletesNixpkgsAttributes() {
        withNixpkgs();
        myFixture.configureByText("default.nix", "{ pkgs, ... }: [ pkgs.hel<caret> ]");

        myFixture.completeBasic();

        assertSameElements(myFixture.getLookupElementStrings(), "hello", "hello-unfree");
    }

    public void testCompletesNoNixpkgsAttributesWithoutTheEvaluator() {
        myFixture.configureByText("default.nix", "{ pkgs, ... }: [ pkgs.hel<caret> ]");

        myFixture.completeBasic();

        assertEmpty(myFixture.getLookupElementStrings());
    }

    public void testCompletesTheAttributesOfALocalSet() {
        myFixture.configureByText("default.nix", "let s = { xa = 1; xb = 2; }; in s.x<caret>");

        myFixture.completeBasic();

        assertSameElements(myFixture.getLookupElementStrings(), "xa", "xb");
    }

    public void testCompletesBuiltinsAfterBuiltins() {
        myFixture.configureByText("default.nix", "builtins.attrNa<caret>");

        myFixture.completeBasic();

        myFixture.checkResult("builtins.attrNames<caret>");
    }

    public void testCompletesNothingInAComment() {
        myFixture.configureByText("default.nix", "# le<caret>\n1");

        myFixture.completeBasic();

        myFixture.checkResult("# le<caret>\n1");
    }

    public void testDocumentsANixpkgsAttribute() {
        withNixpkgs();
        myFixture.configureByText("default.nix", "{ pkgs, ... }: [ pkgs.hel<caret>lo ]");

        List<? extends DocumentationTarget> targets = new NixDocumentationTargetProvider()
                .documentationTargets(myFixture.getFile(), myFixture.getCaretOffset());

        assertSize(1, targets);
        String html = targets.getFirst().computeDocumentation().toString();
        assertTrue(html, html.contains("2.12.1") && html.contains("familiar, friendly greeting"));
    }

    public void testGoesToTheAttributeOfALocalSet() {
        myFixture.configureByText("default.nix", "let s = { x = 1; }; in s.<caret>x");

        PsiElement target = GotoDeclarationAction.findTargetElement(
                getProject(), myFixture.getEditor(), myFixture.getCaretOffset());

        assertNotNull(target);
        assertEquals(10, target.getTextRange().getStartOffset());
    }

    public void testUnquotesAnAttributeName() {
        myFixture.configureByText("default.nix", "{ \"a<caret>b\" = 1; }");

        IntentionAction action = myFixture.findSingleIntention("Unquote the attribute name");
        myFixture.launchAction(action);

        myFixture.checkResult("{ ab = 1; }");
    }

    public void testFormatsOnlyTheSelectedBinding() {
        myFixture.configureByText("default.nix", "{\n  a   =   1;\n  b   =   2;\n}\n");

        WriteCommandAction.runWriteCommandAction(getProject(), () -> CodeStyleManager.getInstance(getProject())
                .reformatText(myFixture.getFile(), 5, 6));

        myFixture.checkResult("{\n  a = 1;\n  b   =   2;\n}\n");
    }

    public void testLinksAUrlString() {
        myFixture.configureByText("default.nix", "{ url = \"https://exa<caret>mple.org\"; }");

        PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());

        assertInstanceOf(reference, WebReference.class);
        assertEquals("https://example.org", ((WebReference) reference).getUrl());
    }
}
