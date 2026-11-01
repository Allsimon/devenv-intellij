package com.allsimon.intellij.nixhighlight;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.graalnix.parser.Highlighter.Kind;

public class NixHighlightingTest extends BasePlatformTestCase {
    public void testClassifiesNixTokens() {
        myFixture.configureByText("default.nix", "{ pkgs, ... }:\nlet name = \"a\\n${pkgs}\"; in # done\nname ++ [ 1 true ]\n");

        assertToken("let", Kind.KEYWORD);
        assertToken("pkgs,", Kind.PARAM);
        assertToken("name =", Kind.ATTR);
        assertToken("\"a", Kind.STRING);
        assertToken("\\n", Kind.ESCAPE);
        assertToken("${", Kind.INTERP);
        assertToken("# done", Kind.COMMENT);
        assertToken("++", Kind.OPERATOR);
        assertToken("1 ", Kind.NUMBER);
        assertToken("true", Kind.CONSTANT);
    }

    public void testReclassifiesWhatAnEditChanges() {
        myFixture.configureByText("default.nix", "{ name<caret> }");
        assertEquals(NixTokenTypes.PLAIN, tokenAt("name"));

        myFixture.type(" = 1;");

        assertToken("name", Kind.ATTR);
    }

    public void testColorsNixFilesDetectedAsTextByContent() {
        var file = myFixture.configureByText("default.nix", "{ }").getVirtualFile();
        var detected = FileTypeManager.getInstance().findFileTypeByName("AUTO_DETECTED");

        assertInstanceOf(SyntaxHighlighterFactory.getSyntaxHighlighter(detected, getProject(), file),
                NixSyntaxHighlighter.class);
    }

    public void testLeavesOtherTextFilesAlone() {
        myFixture.configureByText("notes.txt", "let x = 1; in x\n");

        assertNotSame(NixTokenTypes.of(Kind.KEYWORD), tokenAt("let"));
    }

    private void assertToken(String text, Kind kind) {
        assertEquals(text, NixTokenTypes.of(kind), tokenAt(text));
    }

    private Object tokenAt(String text) {
        int offset = myFixture.getEditor().getDocument().getText().indexOf(text);
        assertTrue(text, offset >= 0);
        HighlighterIterator iterator = ((EditorEx) myFixture.getEditor()).getHighlighter().createIterator(offset);
        return iterator.getTokenType();
    }
}
