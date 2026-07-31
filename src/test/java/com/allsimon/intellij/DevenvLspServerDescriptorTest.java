package com.allsimon.intellij;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DevenvLspServerDescriptorTest {
    @Test
    public void extractSectionReturnsRequestedSection() {
        String printConfigOutput = "{\"nixd\":{\"nixpkgs\":{\"expr\":\"import <nixpkgs> {}\"}}}";

        JsonObject section = DevenvLspServerDescriptor.extractSection(printConfigOutput, "nixd");

        assertEquals("import <nixpkgs> {}",
                section.getAsJsonObject("nixpkgs").get("expr").getAsString());
    }

    @Test
    public void extractSectionReturnsNullWhenSectionMissing() {
        String printConfigOutput = "{\"nixd\":{}}";

        assertNull(DevenvLspServerDescriptor.extractSection(printConfigOutput, "other"));
    }

    @Test
    public void extractSectionReturnsNullWhenRootIsNotAnObject() {
        assertNull(DevenvLspServerDescriptor.extractSection("[]", "nixd"));
    }
}
