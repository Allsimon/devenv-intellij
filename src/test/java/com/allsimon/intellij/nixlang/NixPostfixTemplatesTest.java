package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunInEdt(writeIntent = true)
class NixPostfixTemplatesTest {
    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{ a = x.mkIf<caret>; }                    | { a = lib.mkIf condition x; }",
            "let cfg = 1; in { a = { }.mkIf<caret>; }  | let cfg = 1; in { a = lib.mkIf cfg.enable { }; }",
            "{ a = f x.mkIf<caret>; }                  | { a = f (lib.mkIf condition x); }",
            "{ a = pkgs.hello.list<caret>; }           | { a = [ pkgs.hello ]; }",
            "{ a = 1 + x.paren<caret>; }               | { a = 1 + (x); }",
            "{ a = x.not<caret>; }                     | { a = !x; }",
            "{ a = x.toString<caret>; }                | { a = toString x; }",
    })
    void expands(String source, String result) {
        fixture.run(f -> {
            var disposable = Disposer.newDisposable();
            TemplateManagerImpl.setTemplateTesting(disposable);
            try {
                f.configureByText("default.nix", source);
                f.type('\t');
                assertEquals(result, f.getEditor().getDocument().getText());
            } finally {
                Disposer.dispose(disposable);
            }
        });
    }
}
