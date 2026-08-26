package com.allsimon.intellij.schema;

import com.intellij.testFramework.LightVirtualFile;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DevenvSchemaFileProviderTest {
    @Test
    public void matchesTheTwoFilesDevenvReadsItsConfigurationFrom() {
        assertTrue(DevenvSchemaFileProvider.isDevenvYaml(new LightVirtualFile("devenv.yaml")));
        assertTrue(DevenvSchemaFileProvider.isDevenvYaml(new LightVirtualFile("devenv.local.yaml")));
    }

    @Test
    public void leavesEveryOtherFileAlone() {
        assertFalse(DevenvSchemaFileProvider.isDevenvYaml(new LightVirtualFile("devenv.nix")));
        assertFalse(DevenvSchemaFileProvider.isDevenvYaml(new LightVirtualFile("devenv.lock")));
        // devenv reads 'devenv.yaml', never 'devenv.yml'.
        assertFalse(DevenvSchemaFileProvider.isDevenvYaml(new LightVirtualFile("devenv.yml")));
        assertFalse(DevenvSchemaFileProvider.isDevenvYaml(new LightVirtualFile("docker-compose.yaml")));
    }
}
