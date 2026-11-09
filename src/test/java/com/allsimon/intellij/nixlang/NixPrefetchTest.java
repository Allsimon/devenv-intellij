package com.allsimon.intellij.nixlang;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@RunInEdt(writeIntent = true)
class NixPrefetchTest {
    /** The SRI hash of a file holding "hello\n". */
    private static final String HELLO = "sha256-WJG1tSLV3whtD/CxEPvZ0hu0/HFjrzTQgoai6Eb2vgM=";

    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @TempDir
    Path dir;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "fetch<caret>url { url = \"URL\"; }                           | fetchurl { url = \"URL\"; hash = \"HASH\"; }",
            "fetchurl { url = \"URL\"; hash = lib.fake<caret>Hash; }      | fetchurl { url = \"URL\"; hash = \"HASH\"; }",
            "fetchurl {\\n  url = \"URL\";\\n  sha256 = \"x\";<caret>\\n}  | fetchurl {\\n  url = \"URL\";\\n  hash = \"HASH\";\\n}",
            "fetchurl {\\n  u<caret>rl = \"URL\";\\n}                      | fetchurl {\\n  url = \"URL\";\\n  hash = \"HASH\";\\n}",
    })
    void writesTheHashOfAFetchedFile(String source, String result) throws Exception {
        Path file = Files.writeString(dir.resolve("hello.txt"), "hello\n");
        String url = file.toUri().toString();
        fixture.run(f -> {
            f.configureByText("default.nix", source.replace("\\n", "\n").replace("URL", url));
            String expected = result.replace("\\n", "\n").replace("URL", url).replace("HASH", HELLO);
            f.launchAction(f.findSingleIntention("Prefetch the hash"));
            PlatformTestUtil.waitWithEventsDispatching("the hash is not written",
                    () -> f.getEditor().getDocument().getText().equals(expected), 60);
        });
    }

    @Test
    void prefetchesAGitHubSourceAsAFlake() {
        fixture.run(f -> {
            var file = f.configureByText("default.nix",
                    "fetchFromGitHub { owner = \"o\"; repo = \"r\"; tag = \"v1\";<caret> }");
            var fetch = NixPrefetchIntention.fetch(file.findElementAt(f.getCaretOffset()));
            assertEquals(java.util.List.of("flake", "prefetch", "--json", "github:o/r/v1"), fetch.command());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"fetch<caret>url { url = \"${x}\"; }", "fetchFromGitHub { owner = \"o\";<caret> }",
            "fet<caret>ch { url = \"x\"; }"})
    void needsWhatTheFetcherFetches(String source) {
        fixture.run(f -> {
            var file = f.configureByText("default.nix", source);
            assertNull(NixPrefetchIntention.fetch(file.findElementAt(f.getCaretOffset())));
        });
    }
}
