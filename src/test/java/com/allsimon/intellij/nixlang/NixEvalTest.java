package com.allsimon.intellij.nixlang;

import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunInEdt(writeIntent = true)
class NixEvalTest {
    private static final String TMP = System.getProperty("java.io.tmpdir");

    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1 + 1                       | 2",
            "[ 1 \"a\" ]                 | [ 1 \"a\" ]",
            "builtins.length [ 1 2 3 ]   | 3",
            "{ a = 1; }.a                | 1",
    })
    void evaluates(String expression, String value) {
        assertEquals(new NixEval.Outcome(true, value), NixEval.evaluate(expression, TMP));
    }

    @Test
    void reportsTheErrorOfNix() {
        NixEval.Outcome outcome = NixEval.evaluate("undefinedVariable", TMP);
        assertFalse(outcome.ok());
        assertTrue(outcome.text().contains("undefined variable"), outcome.text());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{ a = <selection>1 + 1</selection>; }                    | 1 + 1",
            "{ pkgs }: <selection>pkgs.hello</selection>              | pkgs.hello",
            "<selection>pkgs.lib.id 1</selection>                     | let pkgs = import <nixpkgs> { }; in pkgs.lib.id 1",
            "<selection>lib.id pkgs.hello</selection>                 | let pkgs = import <nixpkgs> { }; lib = pkgs.lib; in lib.id pkgs.hello",
    })
    void bindsPkgsAndLibWhenTheyAreFree(String source, String expression) {
        fixture.run(f -> {
            var file = f.configureByText("default.nix", source);
            var selection = f.getEditor().getSelectionModel();
            var range = new TextRange(selection.getSelectionStart(), selection.getSelectionEnd());
            assertEquals(expression, NixEval.expression(file, range));
        });
    }

    @Test
    void takesTheValueAtTheCaretWithoutASelection() {
        fixture.run(f -> {
            var file = f.configureByText("default.nix", "{ a<caret> = [ 1 2 ]; }");
            assertEquals("[ 1 2 ]", NixEvalAction.range(f.getEditor(), file).substring(file.getText()));
        });
    }
}
