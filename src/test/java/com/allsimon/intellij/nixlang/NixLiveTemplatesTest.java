package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NixLiveTemplatesTest extends BasePlatformTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    }

    public void testExpandsAnEnableOption() {
        myFixture.configureByText("default.nix", "{ options = { mken<caret> }; }");

        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB);

        assertEquals("{ options = { enable = lib.mkEnableOption \"\"; }; }", myFixture.getEditor().getDocument().getText());
    }

    public void testExpandsADerivationThatParses() {
        myFixture.configureByText("default.nix", "{ stdenv, lib }:\nmkd<caret>\n");

        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB);

        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue(text, text.contains("pname = \"hello\";"));
        assertEmpty(com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.getFile(),
                com.intellij.psi.PsiErrorElement.class));
    }

    public void testDoesNotExpandInAString() {
        myFixture.configureByText("default.nix", "\"mken<caret>\"");

        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB);

        assertFalse(myFixture.getEditor().getDocument().getText().contains("mkEnableOption"));
    }
}
