package com.allsimon.intellij.run;

import com.allsimon.intellij.nixlang.NixFixture;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@RunInEdt(writeIntent = true)
class NixHashMismatchFilterTest {
    private static final String FAKE = "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String OLD = "sha256-47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=";
    private static final String GOT = "sha256-Ykvgd8+1qPa3o5b3h7Lcv9Y4ZwqNn4P9uYfFZ0x0rK8=";

    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{ hash = \"" + OLD + "\"; }      | " + OLD + "  | { hash = \"" + GOT + "\"; }",
            "{ hash = lib.fakeHash; }         | " + FAKE + " | { hash = \"" + GOT + "\"; }",
            "{ sha256 = lib.fakeSha256; }     | " + FAKE + " | { sha256 = \"" + GOT + "\"; }",
            "{ a = lib.fakeHash; b = lib.fakeHash; } | " + FAKE + " | { a = lib.fakeHash; b = lib.fakeHash; }",
    })
    void writesTheHashItGot(String source, String specified, String result) {
        fixture.run(f -> {
            var file = f.addFileToProject("pkgs/a.nix", source).getVirtualFile();
            var filter = new NixHashMismatchFilter(f.getProject());
            assertNull(filter.applyFilter("         specified: " + specified, 40));
            String got = "            got:    " + GOT;
            var item = filter.applyFilter(got, 100 + got.length()).getResultItems().getFirst();
            assertEquals(GOT, got.substring(item.getHighlightStartOffset() - 100, item.getHighlightEndOffset() - 100));

            item.getHyperlinkInfo().navigate(f.getProject());

            assertEquals(result, FileDocumentManager.getInstance().getDocument(file).getText());
        });
    }

    @Test
    void needsTheSpecifiedHashFirst() {
        fixture.run(f -> assertNull(new NixHashMismatchFilter(f.getProject()).applyFilter("got: " + GOT, 50)));
    }
}
