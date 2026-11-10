package com.allsimon.intellij.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlakeLockTest {
    private static final String LOCK = """
            {
              "nodes": {
                "nixpkgs_2": {
                  "locked": { "owner": "NixOS", "repo": "nixpkgs", "rev": "abcdef0123456789", "type": "github" }
                },
                "root": {
                  "inputs": { "nixpkgs": "nixpkgs_2", "utils": "utils", "follows": ["nixpkgs"] }
                },
                "utils": {
                  "locked": { "path": "/x", "type": "path" }
                }
              },
              "root": "root",
              "version": 7
            }
            """;

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "nixpkgs | nixpkgs_2 | abcdef0123456789",
            "utils   | utils     | -",
    })
    void findsTheNodeOfAnInput(String input, String node, String rev) {
        FlakeLock.Entry entry = FlakeLock.find(LOCK, input);
        assertEquals(node, entry.node());
        assertEquals(rev, entry.rev());
        assertEquals("\"" + node + "\": {", LOCK.substring(entry.offset(), entry.offset() + node.length() + 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "follows"})
    void findsNothingForAnInputItDoesNotLock(String input) {
        assertNull(FlakeLock.find(LOCK, input));
    }

    @Test
    void findsNothingInWhatIsNoLock() {
        assertNull(FlakeLock.find("not json", "nixpkgs"));
    }
}
