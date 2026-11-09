package com.allsimon.intellij.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NixStoreWritingAccessProviderTest {
    @Test
    public void storeFilesAreNotWritable() {
        assertTrue(NixStoreWritingAccessProvider.isInStore("/nix/store/abc-source/lib/lists.nix"));
        assertFalse(NixStoreWritingAccessProvider.isInStore("/home/p/project/default.nix"));
        assertFalse(NixStoreWritingAccessProvider.isInStore("/nix/var/nix/profiles/default"));
    }
}
