package com.allsimon.intellij.nixlang;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@RunInEdt(writeIntent = true)
class NixInjectionCasesTest {
    private static final String SHELL = "Shell Script";

    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    static Stream<Arguments> injected() {
        return Stream.of(
                Arguments.of("{ installPhase = ''\n  cp ${./a.sh} $out/bin\n''; }", SHELL, "  cp $__nix $out/bin\n"),
                Arguments.of("{ enterShell = \"echo hi\"; }", SHELL, "echo hi"),
                Arguments.of("{ scripts.hello.exec = ''echo ${name}''; }", SHELL, "echo $__nix"),
                Arguments.of("{ enterShell = lib.mkAfter \"echo hi\"; }", SHELL, "echo hi"),
                Arguments.of("pkgs.runCommand \"name\" { } \"touch $out\"", SHELL, "touch $out"),
                Arguments.of("writeShellScript \"name\" \"echo\"", SHELL, "echo"),
                Arguments.of("pkgs.writers.writeJS \"hello\" { } ''console.log(${x})''", "JavaScript",
                        "console.log(__nix)"),
                Arguments.of("writers.writeBashBin \"hello\" \"echo ${x}\"", SHELL, "echo $__nix"),
                Arguments.of("pkgs.writeText \"config.json\" ''{ \"a\": 1 }''", "JSON", "{ \"a\": 1 }"),
                Arguments.of("builtins.toFile \"run.sh\" \"echo\"", SHELL, "echo"),
                Arguments.of("{ environment.etc.\"app/settings.json\".text = \"{}\"; }", "JSON", "{}"),
                Arguments.of("{ files.\"a.json\" = { text = \"[]\"; }; }", "JSON", "[]"),
                Arguments.of("pkgs.writeShellApplication { name = \"x\"; text = \"echo hi\"; }", SHELL, "echo hi"),
                Arguments.of("{ config = /* json */ ''{ \"a\": ${x} }''; }", "JSON", "{ \"a\": __nix }"),
                Arguments.of("{\n  # language=JavaScript\n  code = \"let a = 1\";\n}", "JavaScript", "let a = 1"),
                Arguments.of("{ installPhase = /* json */ \"[]\"; }", "JSON", "[]"),
                Arguments.of("{ x = /* bash */ \"echo\"; }", SHELL, "echo"));
    }

    @ParameterizedTest
    @MethodSource
    void injected(String source, String language, String script) {
        fixture.run(f -> {
            PsiFile injected = injectedIntoLastString(f, source);
            assertNotNull(injected, source);
            assertEquals(language, injected.getLanguage().getID(), source);
            assertEquals(script, injected.getText(), source);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ pname = \"hello\"; }",
            "{ preferLocalBuild = \"x\"; }",
            "writeShellScript \"name\" \"echo\" 1",
            "{ installPhase = \"${x}\"; }",
            "pkgs.writeText \"notes\" \"hello\"",
            "{ text = \"hello\"; }",
            "{ x = /* not a language */ \"hello\"; }",
            "{ x = /* nolanguage */ \"hello\"; }",
    })
    void notInjected(String source) {
        fixture.run(f -> assertNull(injectedIntoLastString(f, source), source));
    }

    static PsiFile injectedIntoLastString(CodeInsightTestFixture f, String source) {
        PsiFile file = f.configureByText("default.nix", source);
        NixStringElement last = PsiTreeUtil.findChildrenOfType(file, NixStringElement.class).stream()
                .reduce((a, b) -> b).orElseThrow();
        var files = InjectedLanguageManager.getInstance(f.getProject()).getInjectedPsiFiles(last);
        return files == null ? null : (PsiFile) files.getFirst().getFirst();
    }
}
