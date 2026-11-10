package com.allsimon.intellij.nixlang;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class NixInjectorTest extends BasePlatformTestCase {







    public void testFollowsTheSettings() {
        NixInjectionSettings settings = NixInjectionSettings.getInstance();
        try {
            settings.shellAttributes = "deploy";
            settings.shellBuilders = "mkScript";
            assertInjected("{ deploy = \"echo\"; }", "echo");
            assertInjected("mkScript \"echo\"", "echo");
            assertNotInjected("{ installPhase = \"echo\"; }");

            settings.shellAttributes = "(";
            assertNotInjected("{ deploy = \"echo\"; }");
        } finally {
            settings.shellAttributes = NixInjectionSettings.DEFAULT_SHELL_ATTRIBUTES;
            settings.shellBuilders = NixInjectionSettings.DEFAULT_SHELL_BUILDERS;
        }
    }

    public void testKeepsAnInjectionChosenInTheIdeAsAComment() {
        PsiFile file = myFixture.configureByText("default.nix", "{ a = \"[1]\"; b = /* js */ \"x\"; }");
        assertTrue(org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport.EP_NAME.getExtensionList().stream()
                .anyMatch(NixInjectionSupport.class::isInstance));
        var support = new NixInjectionSupport();
        var strings = List.copyOf(PsiTreeUtil.findChildrenOfType(file, NixStringElement.class));
        var json = com.intellij.lang.Language.findLanguageByID("JSON");

        assertTrue(support.addInjectionInPlace(json, strings.get(0)));
        assertTrue(support.addInjectionInPlace(json, strings.get(1)));
        assertEquals("{ a = /* json */ \"[1]\"; b = /* json */ \"x\"; }", file.getText());
        assertEquals("JSON", injected(file.getText()).getLanguage().getID());

        NixStringElement first = PsiTreeUtil.findChildOfType(myFixture.getFile(), NixStringElement.class);
        assertTrue(support.removeInjectionInPlace(first));
        assertEquals("{ a = \"[1]\"; b = /* json */ \"x\"; }", myFixture.getFile().getText());
        assertFalse(support.removeInjectionInPlace(
                PsiTreeUtil.findChildOfType(myFixture.getFile(), NixStringElement.class)));
    }


    private void assertInjected(String source, String script) {
        assertInjected(source, "Shell Script", script);
    }

    private void assertInjected(String source, String language, String script) {
        PsiFile injected = injected(source);
        assertNotNull(source, injected);
        assertEquals(source, language, injected.getLanguage().getID());
        assertEquals(source, script, injected.getText());
    }

    private void assertNotInjected(String source) {
        assertNull(source, injected(source));
    }

    private PsiFile injected(String source) {
        PsiFile file = myFixture.configureByText("default.nix", source);
        var strings = PsiTreeUtil.findChildrenOfType(file, NixStringElement.class);
        NixStringElement last = strings.stream().reduce((a, b) -> b).orElseThrow();
        var files = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(last);
        return files == null ? null : (PsiFile) files.getFirst().getFirst();
    }
}
