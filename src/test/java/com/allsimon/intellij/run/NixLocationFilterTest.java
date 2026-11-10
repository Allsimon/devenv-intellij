package com.allsimon.intellij.run;

import com.allsimon.intellij.nixlang.NixFixture;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@RunInEdt(writeIntent = true)
class NixLocationFilterTest {
    private static final String HASH = "0123456789abcdfghijklmnpqrsvwxyz";

    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "       at /nix/store/" + HASH + "-source/pkgs/a.nix:3:5:   | /nix/store/" + HASH + "-source/pkgs/a.nix:3:5 | 2 | 4",
            "error: in /nix/store/" + HASH + "-source/pkgs/a.nix:12   | /nix/store/" + HASH + "-source/pkgs/a.nix:12   | 11 | 0",
    })
    void linksAFlakeSourceToTheProjectsFile(String line, String link, int lineNumber, int column) {
        fixture.run(f -> {
            var file = f.addFileToProject("pkgs/a.nix", "{ }\n").getVirtualFile();
            var result = new NixLocationFilter(f.getProject()).applyFilter(line, 100 + line.length());
            var item = result.getResultItems().getFirst();
            assertEquals(link, line.substring(item.getHighlightStartOffset() - 100, item.getHighlightEndOffset() - 100));
            var info = (OpenFileHyperlinkInfo) item.getHyperlinkInfo();
            assertEquals(file, info.getVirtualFile());
            assertEquals(lineNumber, info.getDescriptor().getLine());
            assertEquals(column, info.getDescriptor().getColumn());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "at /nix/store/" + HASH + "-source/missing.nix:1:1:",
            "building '/nix/store/" + HASH + "-hello.drv'",
            "nothing to see here",
    })
    void leavesWhatIsNoFileAlone(String line) {
        fixture.run(f -> assertNull(new NixLocationFilter(f.getProject()).applyFilter(line, line.length())));
    }
}
