package com.allsimon.intellij.nixlang;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;
import java.util.List;

public class NixEditorTest extends BasePlatformTestCase {
    public void testCommentsALine() {
        myFixture.configureByText("default.nix", "<caret>x\n");

        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);

        myFixture.checkResult("#x\n");
    }

    public void testCommentsABlock() {
        myFixture.configureByText("default.nix", "<selection>x</selection>\n");

        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK);

        myFixture.checkResult("/*x*/\n");
    }

    public void testFoldsWhatSpansLines() {
        PsiFile file = myFixture.configureByText("default.nix", """
                /* a
                   b */
                rec {
                  a = [
                    1
                  ];
                  b = ''
                    x
                  '';
                  c = { d = 1; };
                }
                """);

        FoldingDescriptor[] descriptors = LanguageFolding.buildFoldingDescriptors(
                LanguageFolding.INSTANCE.forLanguage(NixLanguage.INSTANCE), file,
                myFixture.getEditor().getDocument(), false);

        List<String> placeholders = Arrays.stream(descriptors).map(FoldingDescriptor::getPlaceholderText).toList();
        assertSameElements(placeholders, "/*...*/", "rec {...}", "[...]", "''...''");
    }

    private static final String NESTED = """
            { stdenv }:
            stdenv.mkDerivation (finalAttrs: {
              pname = "hello";
              meta = lib.mkIf true {
                license.spdx = "MIT";
              };
            })
            """;

    public void testShowsTheBindingsInTheStructureView() {
        PsiFile file = myFixture.configureByText("default.nix", NESTED);

        assertEquals("default.nix\n pname\n meta\n  license.spdx\n",
                tree(new NixStructureViewFactory.Element(file), 0));
    }

    private static String tree(com.intellij.ide.util.treeView.smartTree.TreeElement element, int depth) {
        StringBuilder out = new StringBuilder(" ".repeat(depth))
                .append(element.getPresentation().getPresentableText()).append('\n');
        for (var child : element.getChildren()) {
            out.append(tree(child, depth + 1));
        }
        return out.toString();
    }

    public void testShowsTheBindingsAroundTheCaretAsBreadcrumbs() {
        myFixture.configureByText("default.nix", NESTED.replace("\"MIT\"", "\"<caret>MIT\""));

        assertEquals(List.of("meta", "license.spdx"),
                myFixture.getBreadcrumbsAtCaret().stream().map(c -> c.getText()).toList());
    }

    public void testTheColorDemoParsesAndDescribesEveryColor() {
        PsiFile file = myFixture.configureByText("default.nix", NixColorSettingsPage.DEMO.replaceAll("</?\\w+>", ""));

        assertEmpty(com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiErrorElement.class));
        assertEquals(org.affogato.parser.Highlighter.Kind.values().length,
                new NixColorSettingsPage().getAttributeDescriptors().length);
    }

    public void testFindsTodosInComments() {
        PsiFile file = myFixture.configureByText("default.nix", "# TODO one\n/* FIXME two */\n\"TODO not a comment\"\n");

        assertEquals(2, com.intellij.psi.search.PsiTodoSearchHelper.getInstance(getProject()).findTodoItems(file).length);
    }

    public void testSpellchecksPlainStringsButNotInjectedOnes() {
        PsiFile file = myFixture.configureByText("default.nix",
                "{ description = \"anothr\"; installPhase = \"echo qwzrtx\"; }");
        var strategy = new NixSpellcheckingStrategy();

        assertSame(com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.TEXT_TOKENIZER,
                strategy.getTokenizer(file.findElementAt(file.getText().indexOf("anothr"))));
        assertSame(com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.EMPTY_TOKENIZER,
                strategy.getTokenizer(file.findElementAt(file.getText().indexOf("echo"))));
    }
}
