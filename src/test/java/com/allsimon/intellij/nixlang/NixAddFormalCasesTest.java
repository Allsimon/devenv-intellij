package com.allsimon.intellij.nixlang;

import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunInEdt(writeIntent = true)
class NixAddFormalCasesTest {
    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{ stdenv }: fetch<caret>url              | { stdenv, fetchurl }: fetchurl",
            "{ stdenv, ... }: fetch<caret>url         | { stdenv, fetchurl, ... }: fetchurl",
            "{ }: fetch<caret>url                     | { fetchurl }: fetchurl",
            "{\\n  stdenv,\\n  ...\\n}:\\nfetch<caret>url\\n | {\\n  stdenv,\\n  fetchurl,\\n  ...\\n}:\\nfetchurl\\n",
    })
    void addsTheVariable(String source, String result) {
        fixture.run(f -> {
            f.configureByText("default.nix", source.replace("\\n", "\n"));
            f.launchAction(f.findSingleIntention("Add 'fetchurl' to the function's arguments"));
            f.checkResult(result.replace("\\n", "\n"));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ a }: a<caret>", "{ a }: toStr<caret>ing a", "{ pkgs }: with pkgs; hel<caret>lo",
            "x: und<caret>efined"})
    void isNotOffered(String source) {
        fixture.run(f -> {
            f.configureByText("default.nix", source);
            assertTrue(f.filterAvailableIntentions("Add '").isEmpty(), source);
        });
    }
}
