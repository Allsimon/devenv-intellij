package com.allsimon.intellij.run;

import com.allsimon.intellij.nixlang.NixFixture;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code <caret>} marks the token the gutter looks at. */
@RunInEdt(writeIntent = true)
class NixRunTargetsTest {
    @RegisterExtension
    final NixFixture fixture = new NixFixture();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "devenv.nix | <caret>{ pkgs, ... }: { }                                          | devenv up, devenv test",
            "devenv.nix | # comment\\n<caret>{ }                                            | devenv up, devenv test",
            "devenv.nix | { a = <caret>1; }                                                   | ''",
            "flake.nix  | { outputs = _: { packages.x86_64-linux.<caret>hello = 1; }; }       | nix build .#hello",
            "flake.nix  | { outputs = _: { packages = forAllSystems (s: { <caret>default = 1; }); }; } | nix build .#default",
            "flake.nix  | { outputs = _: { apps.${system}.<caret>run = 1; }; }               | nix run .#run",
            "flake.nix  | { outputs = _: { checks.x86_64-linux.<caret>fmt = 1; }; }          | nix flake check",
            "flake.nix  | { outputs = _: { <caret>packages.x86_64-linux.hello = 1; }; }      | ''",
            "flake.nix  | { packages.hello = { <caret>pname = 1; }; }                         | ''",
            "default.nix | { packages.x86_64-linux.<caret>hello = 1; }                       | ''",
            "flake.nix  | { inputs.<caret>nixpkgs.url = \"github:NixOS/nixpkgs\"; }        | nix flake update nixpkgs",
            "flake.nix  | { inputs = { <caret>utils = { url = \"x\"; }; }; }               | nix flake update utils",
            "flake.nix  | { inputs.nixpkgs.<caret>url = \"x\"; }                            | ''",
    })
    void commands(String name, String source, String expected) {
        fixture.run(f -> {
            var file = f.configureByText(name, source.replace("\\n", "\n"));
            var leaf = file.findElementAt(f.getCaretOffset());
            String labels = String.join(", ",
                    NixRunTargets.commands(leaf).stream().map(NixRunTargets.Command::label).toList());
            assertEquals(expected, labels);
        });
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{ processes.<caret>web.exec = \"x\"; }         | web",
            "{ processes = { <caret>db = { }; }; }          | db",
            "{ processes.web.<caret>exec = \"x\"; }         | ''",
            "{ <caret>processes.web.exec = \"x\"; }         | ''",
    })
    void process(String source, String expected) {
        fixture.run(f -> {
            var file = f.configureByText("devenv.nix", source);
            String process = NixRunTargets.process(file.findElementAt(f.getCaretOffset()));
            assertEquals(expected, process == null ? "" : process);
        });
    }
}
