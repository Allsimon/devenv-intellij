package com.allsimon.intellij.nixlang;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NixParsingTest extends BasePlatformTestCase {
    private static final String DERIVATION = """
            { stdenv }:
            stdenv.mkDerivation {
              pname = "hello"; # a comment
              installPhase = ''
                cp ${./hello.sh} $out
              '';
            }
            """;

    public void testNixFilesGetTheNixLanguage() {
        PsiFile file = myFixture.configureByText("default.nix", DERIVATION);

        assertInstanceOf(file, NixFile.class);
        assertEquals(NixLanguage.INSTANCE, file.getLanguage());
    }

    public void testKeepsTheWholeSourceWithoutError() {
        PsiFile file = myFixture.configureByText("default.nix", DERIVATION);

        assertEquals(DERIVATION, file.getText());
        assertEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class));
    }

    public void testReportsErrorsAndKeepsParsing() {
        PsiFile file = myFixture.configureByText("default.nix", "{ a = ; b = \"x\"; }");

        assertEquals("expected expression",
                PsiTreeUtil.findChildOfType(file, PsiErrorElement.class).getErrorDescription());
        assertEquals("\"x\"", PsiTreeUtil.findChildOfType(file, NixStringElement.class).getText());
    }

    public void testDecodesTheEscapesOfAString() {
        assertDecoded("\"a\\n\\${b}${c}\"", "a\n${b}${c}");
        assertDecoded("''\n  a ''$ ''' ''\\t\n''", "  a $ '' \t\n");
    }

    private void assertDecoded(String source, String expected) {
        PsiFile file = myFixture.configureByText("default.nix", source);
        NixStringElement string = PsiTreeUtil.findChildOfType(file, NixStringElement.class);
        StringBuilder decoded = new StringBuilder();

        assertTrue(string.createLiteralTextEscaper().decode(string.contentRange(), decoded));
        assertEquals(expected, decoded.toString());
    }
}
