package com.allsimon.intellij.nixhighlight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.allsimon.intellij.nixlang.NixFixture;
import org.affogato.parser.Highlighter.Kind;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The colors at the start of each piece of one file, from the highlighting lexer and from the annotator. */
@RunInEdt(writeIntent = true)
class NixColorsTest {
    private static final String SOURCE =
            "{ pkgs, ... }:\nlet name = \"a\\n${pkgs.hello}\"; in # done\nname ++ [ 1 true (import ./x) ]\n";

    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "let    | KEYWORD", "pkgs,  | PARAM", "name = | ATTR", "hello | ATTR", "`\"a`  | STRING",
            "`\\n`  | ESCAPE", "${    | INTERP", "`}\"`  | INTERP", "# done | COMMENT", "++   | OPERATOR",
            "`1 `   | NUMBER", "true   | CONSTANT", "import | BUILTIN", "./x  | PATH",
    })
    void colors(String text, Kind kind) {
        fixture.run(f -> {
            f.configureByText("default.nix", SOURCE);
            int offset = SOURCE.indexOf(text);
            assertTrue(offset >= 0, text);
            var iterator = ((EditorEx) f.getEditor()).getHighlighter().createIterator(offset);
            List<TextAttributesKey> colors = new ArrayList<>(List.of(iterator.getTextAttributesKeys()));
            for (HighlightInfo info : f.doHighlighting()) {
                if (info.getStartOffset() <= offset && offset < info.getEndOffset()
                        && info.forcedTextAttributesKey != null) {
                    colors.add(info.forcedTextAttributesKey);
                }
            }
            assertTrue(colors.contains(NixColors.of(kind)), text + " " + colors);
        });
    }
}
