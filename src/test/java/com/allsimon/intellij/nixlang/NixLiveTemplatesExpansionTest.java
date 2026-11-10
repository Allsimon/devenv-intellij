package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@RunInEdt(writeIntent = true)
class NixLiveTemplatesExpansionTest {
    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "mkd   | { stdenv, lib }: %s",
            "mkopt | { options = { %s }; }",
            "mken  | { options = { %s }; }",
            "mod   | %s",
            "fgh   | { fetchFromGitHub, lib }: %s",
    })
    void expandsToValidNix(String template, String context) {
        EdtTestUtil.runInEdtAndWait(() -> {
            var f = fixture.get();
            var disposable = Disposer.newDisposable();
            TemplateManagerImpl.setTemplateTesting(disposable);
            try {
                f.configureByText("default.nix", context.formatted(template + "<caret>"));
                f.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB);
                PsiDocumentManager.getInstance(f.getProject()).commitAllDocuments();
                String text = f.getEditor().getDocument().getText();
                assertFalse(text.contains(template + " ") || text.endsWith(template), text);
                assertEquals(0, PsiTreeUtil.findChildrenOfType(f.getFile(), PsiErrorElement.class).size(), text);
            } finally {
                Disposer.dispose(disposable);
            }
        });
    }
}
