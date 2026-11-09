package com.allsimon.intellij.nixlang;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext;
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext;

import java.util.List;

public class NixParameterInfoTest extends BasePlatformTestCase {
    public void testListsTheFormalsOfALocalFunction() {
        assertEquals("a, <b>b ?</b>, c",
                shown("let f = { a, b ? 1, c, ... }: a; in f { a = 1; b = <caret>2; }"));
    }

    public void testListsTheFormalsOfACalledPackage() {
        myFixture.addFileToProject("hello/default.nix", "{ stdenv, fetchurl ? null }: stdenv");

        assertEquals("<b>stdenv</b>, fetchurl ?", shown("callPackage ./hello { stdenv = <caret>x; }"));
    }

    public void testShowsNothingForAnUnknownFunction() {
        myFixture.configureByText("default.nix", "f { a = <caret>1; }");
        var handler = new NixParameterInfoHandler();

        assertNull(handler.findElementForParameterInfo(
                new MockCreateParameterInfoContext(myFixture.getEditor(), myFixture.getFile())));
    }

    private String shown(String source) {
        myFixture.configureByText("default.nix", source);
        PsiFile file = myFixture.getFile();
        var handler = new NixParameterInfoHandler();
        var create = new MockCreateParameterInfoContext(myFixture.getEditor(), file);
        var set = handler.findElementForParameterInfo(create);
        assertNotNull(set);
        handler.showParameterInfo(set, create);
        @SuppressWarnings("unchecked")
        var formals = (List<NixFormals.Formal>) create.getItemsToShow()[0];
        var update = new MockUpdateParameterInfoContext(myFixture.getEditor(), file);
        handler.updateParameterInfo(set, update);
        var ui = new MockParameterInfoUIContext<>(set);
        ui.setCurrentParameterIndex(update.getCurrentParameter());
        handler.updateUI(formals, ui);
        String text = ui.getText();
        int start = ui.getHighlightStart();
        return start < 0 ? text
                : text.substring(0, start) + "<b>" + text.substring(start, ui.getHighlightEnd()) + "</b>"
                + text.substring(ui.getHighlightEnd());
    }
}
