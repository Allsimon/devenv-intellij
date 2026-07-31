package com.allsimon.intellij;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TreefmtConfigTest {
    /** The wrapper devenv generates at '.devenv/profile/bin/treefmt', with store paths shortened. */
    private static final String WRAPPER_SCRIPT = """
            #!/nix/store/xxx-bash-5.3p15/bin/bash
            exec /nix/store/xxx-treefmt-2.5.0/bin/treefmt --config-file /nix/store/xxx-treefmt.toml "$@" --tree-root /home/user/project
            """;

    /** The treefmt.toml that wrapper points at, for a devenv.nix enabling nixfmt and oxfmt. */
    private static final String CONFIG = """
            excludes = ["*.lock", "*.patch", "package-lock.json", "go.mod", "go.sum", ".gitattributes", ".gitignore", ".gitmodules", ".hgignore", ".svnignore", "LICENSE"]

            [formatter.nixfmt]
            command = "/nix/store/xxx-nixfmt-1.4.0/bin/nixfmt"
            excludes = []
            includes = ["*.nix"]
            options = []

            [formatter.oxfmt]
            command = "/nix/store/xxx-oxfmt-0.59.0/bin/oxfmt"
            excludes = []
            includes = ["*.cjs", "*.css", "*.graphql", "*.hbs", "*.html", "*.js", "*.json", "*.json5", "*.jsonc", "*.jsx", "*.md", "*.mdx", "*.mjs", "*.mustache", "*.scss", "*.ts", "*.tsx", "*.vue", "*.yaml", "*.yml"]
            options = []
            """;

    @Test
    public void configFilePathReadsTheBakedInArgument() {
        assertEquals("/nix/store/xxx-treefmt.toml", TreefmtConfig.configFilePath(WRAPPER_SCRIPT));
    }

    @Test
    public void configFilePathReturnsNullWhenTheWrapperIsNotWhatWeExpect() {
        assertNull(TreefmtConfig.configFilePath("#!/bin/sh\nexec treefmt \"$@\"\n"));
    }

    @Test
    public void matchesFilesOfEveryConfiguredFormatter() {
        TreefmtConfig config = TreefmtConfig.parse(CONFIG);

        assertTrue(config.matches("devenv.nix", "devenv.nix"));
        assertTrue(config.matches("README.md", "README.md"));
        assertTrue(config.matches("devenv.yaml", "devenv.yaml"));
    }

    @Test
    public void matchesNestedFilesEvenThoughTheGlobHasNoPathSeparator() {
        TreefmtConfig config = TreefmtConfig.parse(CONFIG);

        assertTrue(config.matches("nix/modules/java.nix", "java.nix"));
        assertTrue(config.matches("docs/guide/setup.md", "setup.md"));
    }

    @Test
    public void doesNotClaimFilesNoFormatterIsConfiguredFor() {
        TreefmtConfig config = TreefmtConfig.parse(CONFIG);

        // The whole point: Java keeps using IntelliJ's own formatter in a project like this one.
        assertFalse(config.matches("src/main/java/Foo.java", "Foo.java"));
        assertFalse(config.matches("gradlew", "gradlew"));
    }

    @Test
    public void honoursTheGlobalExcludes() {
        TreefmtConfig config = TreefmtConfig.parse(CONFIG);

        assertFalse("*.lock is excluded even though it isn't in any includes list",
                config.matches("devenv.lock", "devenv.lock"));
        // package-lock.json matches oxfmt's '*.json' but is excluded by name.
        assertFalse(config.matches("package-lock.json", "package-lock.json"));
    }

    @Test
    public void isEmptyWhenNoFormatterIsConfigured() {
        assertTrue(TreefmtConfig.parse("excludes = [\"*.lock\"]\n").isEmpty());
        assertFalse(TreefmtConfig.parse(CONFIG).isEmpty());
    }

    @Test
    public void parseToleratesAnEmptyOrUnexpectedConfig() {
        assertTrue(TreefmtConfig.parse("").isEmpty());
        assertFalse(TreefmtConfig.parse("").matches("devenv.nix", "devenv.nix"));
    }
}
