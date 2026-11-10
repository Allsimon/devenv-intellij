package com.allsimon.intellij.nixhighlight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.allsimon.intellij.nixlang.NixHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.affogato.parser.Highlighter.Kind;

import java.util.ArrayList;
import java.util.List;

public class NixHighlightingTest extends BasePlatformTestCase {

    public void testReclassifiesWhatAnEditChanges() {
        myFixture.configureByText("default.nix", "true<caret>");
        assertColor("true", Kind.CONSTANT);

        myFixture.type("x");

        assertFalse(colors("truex").contains(NixColors.of(Kind.CONSTANT)));
    }

    public void testEditsInsideAndAroundStringsLexAsAFreshFile() {
        myFixture.configureByText("default.nix", "{\n  a = \"x ${y}<caret> z\";\n  b = ''\n    w\n  '';\n  c = 1;\n}\n");

        for (String typed : List.of("\"", "; d = ", "\"", "''", "${", "}", " ''")) {
            myFixture.type(typed);
            assertLexesAsAFreshFile();
        }
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("c ="));
        myFixture.type("/* ");
        assertLexesAsAFreshFile();
    }

    private void assertLexesAsAFreshFile() {
        String text = myFixture.getEditor().getDocument().getText();
        HighlighterIterator edited = ((EditorEx) myFixture.getEditor()).getHighlighter().createIterator(0);
        Lexer fresh = new NixHighlighter().getHighlightingLexer();
        fresh.start(text);
        for (; fresh.getTokenType() != null; fresh.advance(), edited.advance()) {
            assertEquals(text, fresh.getTokenStart(), edited.getStart());
            assertEquals(text, fresh.getTokenType(), edited.getTokenType());
        }
        assertTrue(text, edited.atEnd());
    }

    public void testMatchesBraces() {
        myFixture.configureByText("default.nix", "{ a = \"<caret>${b}\"; }");

        int close = myFixture.getEditor().getDocument().getText().indexOf("}\"");
        assertEquals(close, com.intellij.codeInsight.highlighting.BraceMatchingUtil.getMatchedBraceOffset(
                myFixture.getEditor(), true, myFixture.getFile()) );
    }

    public void testClosesAQuote() {
        myFixture.configureByText("default.nix", "{ a = <caret>; }");

        myFixture.type("\"");

        myFixture.checkResult("{ a = \"<caret>\"; }");
    }

    private void assertColor(String text, Kind kind) {
        assertTrue(text + " " + colors(text), colors(text).contains(NixColors.of(kind)));
    }

    /** The colors at the start of {@code text}, from the highlighting lexer and from the annotator. */
    private List<TextAttributesKey> colors(String text) {
        int offset = myFixture.getEditor().getDocument().getText().indexOf(text);
        assertTrue(text, offset >= 0);
        HighlighterIterator iterator = ((EditorEx) myFixture.getEditor()).getHighlighter().createIterator(offset);
        List<TextAttributesKey> colors = new ArrayList<>(List.of(iterator.getTextAttributesKeys()));
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getStartOffset() <= offset && offset < info.getEndOffset() && info.forcedTextAttributesKey != null) {
                colors.add(info.forcedTextAttributesKey);
            }
        }
        return colors;
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

    private Object tokenAt(String text) {
        int offset = myFixture.getEditor().getDocument().getText().indexOf(text);
        assertTrue(text, offset >= 0);
        HighlighterIterator iterator = ((EditorEx) myFixture.getEditor()).getHighlighter().createIterator(offset);
        return iterator.getTokenType();
    }
}
