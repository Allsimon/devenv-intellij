package com.allsimon.intellij.nixlang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@code <ref>} marks the variable, {@code <def>} the definition it resolves to. */
@RunInEdt(writeIntent = true)
class NixResolutionCasesTest {
    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @ValueSource(strings = {
            "let <def>a = 1; in <ref>a",
            "<def>x: <ref>x",
            "<def>args @ { a }: <ref>args",
            "{ <def>a, b ? <ref>a }: b",
            "{ a, <def>b ? 1 }: <ref>b",
            "rec { <def>a.b = 1; c = <ref>a; }",
            "let a = 1; in <def>a: <ref>a",
            "let <def>a = 1; in { inherit <ref>a; }",
            "let inherit (pkgs) <def>hello; in <ref>hello",
            "<def>a: let inherit <ref>a; in a",
    })
    void resolves(String source) {
        int definition = source.indexOf("<def>");
        fixture.run(f -> {
            PsiElement resolved = reference(f, source.replace("<def>", "")).resolve();
            assertNotNull(resolved, source);
            assertEquals(definition, resolved.getTextOffset(), source);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ a = 1; c = <ref>a; }", "with pkgs; <ref>hello", "<ref>undefined"})
    void staysUnresolved(String source) {
        fixture.run(f -> assertNull(reference(f, source).resolve(), source));
    }

    private static PsiReference reference(com.intellij.testFramework.fixtures.CodeInsightTestFixture f, String source) {
        int offset = source.indexOf("<ref>");
        var file = f.configureByText("default.nix", source.replace("<ref>", ""));
        PsiReference reference = file.findReferenceAt(offset);
        assertNotNull(reference, source);
        return reference;
    }
}
