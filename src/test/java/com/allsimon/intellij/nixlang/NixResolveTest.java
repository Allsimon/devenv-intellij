package com.allsimon.intellij.nixlang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NixResolveTest extends BasePlatformTestCase {

    public void testANameFromWithStaysSoftlyUnresolved() {
        PsiReference reference = referenceAt("with pkgs; <ref>hello");
        assertNull(reference.resolve());
        assertTrue(reference.isSoft());
    }

    public void testRenamesADefinitionAndItsUses() {
        myFixture.configureByText("default.nix", "let a<caret> = 1; b = a + 1; in { inherit a; c = a; }");

        myFixture.renameElementAtCaret("renamed");

        myFixture.checkResult("let renamed = 1; b = renamed + 1; in { inherit renamed; c = renamed; }");
    }

    public void testFindsTheUsesOfADefinition() {
        myFixture.configureByText("default.nix", "{ a<caret>, b ? a }: [ a b (a: a) ]");

        assertSize(2, myFixture.findUsages(myFixture.getElementAtCaret()));
    }

    public void testCompletesTheNamesInScope() {
        myFixture.configureByText("default.nix", "let foo = 1; inherit (x) far; in { bar }: f<caret>");

        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "foo", "far");
    }

    private PsiReference referenceAt(String source) {
        int offset = source.indexOf("<ref>");
        var file = myFixture.configureByText("default.nix", source.replace("<ref>", ""));
        PsiReference reference = file.findReferenceAt(offset);
        assertNotNull(source, reference);
        return reference;
    }
}
