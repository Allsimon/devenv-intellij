package com.allsimon.intellij.nixlang;

import com.allsimon.intellij.core.DevenvCli;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Evaluates Nix code with {@code nix eval}, the way the file it comes from would see it where it can. */
final class NixEval {
    /** What evaluating gave: the value Nix printed, or its error. */
    record Outcome(boolean ok, String text) {}

    private static final int TIMEOUT_MILLIS = 60_000;

    private NixEval() {
    }

    /** The code of {@code range}, with {@code pkgs} and {@code lib} bound when it uses them without defining them. */
    static String expression(@NotNull PsiFile file, @NotNull TextRange range) {
        String code = range.substring(file.getText()).strip();
        Set<String> free = new LinkedHashSet<>();
        for (NixVarElement var : PsiTreeUtil.findChildrenOfType(file, NixVarElement.class)) {
            if (range.contains(var.getTextRange()) && var.getReference().resolve() == null
                    && List.of("pkgs", "lib").contains(var.getText())) {
                free.add(var.getText());
            }
        }
        if (free.isEmpty()) {
            return code;
        }
        StringBuilder let = new StringBuilder("let pkgs = import <nixpkgs> { };");
        if (free.contains("lib")) {
            let.append(" lib = pkgs.lib;");
        }
        return let.append(" in ").append(code).toString();
    }

    static Outcome evaluate(@NotNull String expression, @NotNull String directory) {
        File nix = PathEnvironmentVariableUtil.findInPath("nix");
        if (nix == null) {
            return new Outcome(false, "'nix' is not on the PATH");
        }
        try {
            ProcessOutput output = DevenvCli.run(
                    DevenvCli.commandLine(nix, directory, "eval", "--impure", "--expr", expression), TIMEOUT_MILLIS);
            if (output.isTimeout()) {
                return new Outcome(false, "nix eval took longer than " + TIMEOUT_MILLIS / 1000 + " seconds");
            }
            return output.getExitCode() == 0
                    ? new Outcome(true, output.getStdout().strip())
                    : new Outcome(false, DevenvCli.stripAnsi(output.getStderr()).strip());
        } catch (ExecutionException e) {
            return new Outcome(false, e.getMessage());
        }
    }
}
