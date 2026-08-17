package com.allsimon.intellij.javascript;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DevenvJavascriptTest {
    /** What 'devenv eval languages.javascript.enable languages.javascript.package' prints. */
    private static final String ENABLED = """
            {
              "languages.javascript.enable": true,
              "languages.javascript.package": "/nix/store/xxx-nodejs-slim-24.18.0"
            }
            """;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsThePackageOfAProjectThatEnablesJavascript() {
        assertEquals("/nix/store/xxx-nodejs-slim-24.18.0", DevenvJavascript.parsePackage(ENABLED));
    }

    /**
     * The package option has a default, so it evaluates to a perfectly good store path even in a
     * project that never asked for Node.js - which is why the enable flag is evaluated with it.
     */
    @Test
    public void ignoresThePackageOfAProjectThatDoesNotEnableJavascript() {
        assertNull(DevenvJavascript.parsePackage("""
                {
                  "languages.javascript.enable": false,
                  "languages.javascript.package": "/nix/store/xxx-nodejs-slim-24.18.0"
                }
                """));
    }

    @Test
    public void stripsTheColoursDevenvWritesEvenIntoAPipe() {
        assertEquals("/nix/store/xxx-nodejs-slim-24.18.0", DevenvJavascript.parsePackage(
                "\033[0m{\n  \"languages.javascript.enable\": true,\n"
                        + "  \"languages.javascript.package\": \"/nix/store/xxx-nodejs-slim-24.18.0\"\n}\n"));
    }

    @Test
    public void returnsNothingWhenAnAttributeIsMissingOrMalformed() {
        assertNull(DevenvJavascript.parsePackage("{\"languages.javascript.enable\": true}"));
        assertNull(DevenvJavascript.parsePackage(
                "{\"languages.javascript.package\": \"/nix/store/xxx-nodejs-slim-24.18.0\"}"));
        assertNull(DevenvJavascript.parsePackage("""
                {
                  "languages.javascript.enable": "true",
                  "languages.javascript.package": "/nix/store/xxx-nodejs-slim-24.18.0"
                }
                """));
        assertNull(DevenvJavascript.parsePackage("""
                {
                  "languages.javascript.enable": true,
                  "languages.javascript.package": "   "
                }
                """));
        assertNull(DevenvJavascript.parsePackage("not json at all"));
    }

    /** How the nixpkgs Node.js is laid out: the interpreter, and on a slim build very little else. */
    @Test
    public void findsTheInterpreterOfANixpkgsStylePackage() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Path node = executable(storePath.resolve("bin/node"));

        assertEquals(node, DevenvJavascript.findInterpreter(storePath));
    }

    @Test
    public void findsNoInterpreterWhenNothingIsRunnableThere() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("bin"));
        // An npm shim and nothing else: the runtime is a package of its own and may not be here.
        executable(storePath.resolve("bin/npm"));

        assertNull(DevenvJavascript.findInterpreter(storePath));
        assertNull("an unbuilt store path is simply not there",
                DevenvJavascript.findInterpreter(storePath.resolve("missing")));
    }

    /** A 'bin/node' the IDE could not run is no better than none at all. */
    @Test
    public void findsNoInterpreterWhenTheFileCannotBeExecuted() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Files.createDirectories(storePath.resolve("bin"));
        Files.createFile(storePath.resolve("bin/node"), PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-r--r--")));

        assertNull(DevenvJavascript.findInterpreter(storePath));
    }

    /**
     * What 'devenv eval' prints for the package manager options, with only npm enabled. Every
     * package attribute evaluates whether or not its manager is on, which is what makes the flags
     * necessary - and, here, what makes picking the wrong one so easy.
     */
    private static final String NPM_ONLY = """
            {
              "languages.javascript.npm.enable": true,
              "languages.javascript.npm.package": "/nix/store/xxx-nodejs-slim-24.18.0-npm",
              "languages.javascript.pnpm.enable": false,
              "languages.javascript.pnpm.package": "/nix/store/xxx-pnpm-11.15.0",
              "languages.javascript.yarn.enable": false,
              "languages.javascript.yarn.package": "/nix/store/xxx-yarn-1.22.22"
            }
            """;

    @Test
    public void readsThePackageManagerAProjectEnables() {
        DevenvJavascript.Declared declared = DevenvJavascript.parsePackageManager(NPM_ONLY);

        assertNotNull(declared);
        assertEquals(DevenvPackageManager.NPM, declared.manager());
        assertEquals("/nix/store/xxx-nodejs-slim-24.18.0-npm", declared.storePath());
    }

    @Test
    public void readsNoPackageManagerWhenTheProjectEnablesNone() {
        assertNull(DevenvJavascript.parsePackageManager(NPM_ONLY.replace("""
                "languages.javascript.npm.enable": true""", """
                "languages.javascript.npm.enable": false""")));
        assertNull(DevenvJavascript.parsePackageManager("not json at all"));
    }

    /**
     * The IDE has a single Package manager setting and devenv is happy to declare several, so one has
     * to win: the most deliberate choice, which is anything the project asked for over the npm it gets
     * for merely having Node.js.
     */
    @Test
    public void prefersTheMoreDeliberateChoiceWhenSeveralAreEnabled() {
        DevenvJavascript.Declared declared = DevenvJavascript.parsePackageManager(NPM_ONLY.replace("""
                "languages.javascript.pnpm.enable": false""", """
                "languages.javascript.pnpm.enable": true"""));

        assertNotNull(declared);
        assertEquals(DevenvPackageManager.PNPM, declared.manager());
        assertEquals("/nix/store/xxx-pnpm-11.15.0", declared.storePath());
    }

    /** How the nixpkgs npm is laid out, and the one manager whose package sits under lib. */
    @Test
    public void findsTheNpmPackageOfANixpkgsStylePackage() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Path npm = packageDirectory(storePath.resolve("lib/node_modules/npm"));
        executable(storePath.resolve("bin/npm"));

        assertEquals(npm, DevenvPackageManager.NPM.findPackage(storePath));
    }

    /** And how the nixpkgs pnpm and yarn are: a wrapper in bin, the package itself under libexec. */
    @Test
    public void findsThePnpmAndYarnPackagesOfANixpkgsStylePackage() throws Exception {
        Path storePath = folder.getRoot().toPath();
        Path pnpm = packageDirectory(storePath.resolve("libexec/pnpm"));
        Path yarn = packageDirectory(storePath.resolve("libexec/yarn"));

        assertEquals(pnpm, DevenvPackageManager.PNPM.findPackage(storePath));
        assertEquals(yarn, DevenvPackageManager.YARN.findPackage(storePath));
    }

    @Test
    public void findsNoPackageWithoutTheMarkerTheIdeLooksFor() throws Exception {
        Path storePath = folder.getRoot().toPath();
        // A wrapper script and nothing else is exactly what the top of the nixpkgs store path holds.
        executable(storePath.resolve("bin/pnpm"));
        Files.createDirectories(storePath.resolve("libexec/pnpm"));

        assertNull(DevenvPackageManager.PNPM.findPackage(storePath));
        assertNull("an unbuilt store path is simply not there",
                DevenvPackageManager.NPM.findPackage(storePath.resolve("missing")));
    }

    /**
     * The IDE reads the manager's name off the last segment of the path and decides from it whether it
     * is driving npm, yarn or pnpm, so a package found anywhere else than under its own name would be
     * driven as the wrong tool.
     */
    @Test
    public void namesEveryPackageAfterItsManager() throws Exception {
        Path storePath = folder.getRoot().toPath();
        for (DevenvPackageManager manager : DevenvPackageManager.values()) {
            packageDirectory(storePath.resolve("libexec").resolve(manager.packageName()));

            Path found = manager.findPackage(storePath);
            assertNotNull(found);
            assertEquals(manager.packageName(), found.getFileName().toString());
        }
    }

    private static Path packageDirectory(Path directory) throws Exception {
        Files.createDirectories(directory);
        Files.createFile(directory.resolve("package.json"));
        return directory;
    }

    private static Path executable(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.createFile(file, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("r-xr-xr-x")));
    }
}
