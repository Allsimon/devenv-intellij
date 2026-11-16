package com.allsimon.intellij.nixlang;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.EnvironmentUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.EvalException;
import org.affogato.Interpreter;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Evaluates Nix code with affogato's interpreter, the way the file it comes from would see it where it can. */
final class NixEval {
    /** What evaluating gave: the value Nix printed, or its error. */
    record Outcome(boolean ok, String text) {}

    private static final int TIMEOUT_MILLIS = 60_000;

    /** Replaces each derivation with a mark, as {@code nix eval} prints it, rather than forcing all it holds. */
    private static final String SHOW = """
            let __show = v: if builtins.isAttrs v then (if v.type or null == "derivation" \
            then "\u00abderivation ${v.drvPath}\u00bb" else builtins.mapAttrs (_: __show) v) \
            else if builtins.isList v then map __show v else v; in __show (%s
            )""";
    private static final Pattern DERIVATION = Pattern.compile("\"(\u00abderivation [^\"]*\u00bb)\"");

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

    /** Evaluates {@code expression} as if written in the file at {@code file}, which its relative paths start from. */
    static Outcome evaluate(@NotNull String expression, @NotNull String file) {
        var options = new Interpreter.Options(Objects.requireNonNullElse(EnvironmentUtil.getValue("NIX_PATH"), ""),
                "", "", Interpreter.Output.STRICT, "", List.of());
        try {
            String printed = Interpreter.evaluate(options, file, bytes(SHOW.formatted(expression)), TIMEOUT_MILLIS);
            return new Outcome(true, DERIVATION.matcher(text(printed)).replaceAll("$1"));
        } catch (EvalException e) {
            return new Outcome(false, text(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, "interrupted");
        }
    }

    /** Nix strings are bytes, one char per byte in the interpreter. */
    private static String bytes(String text) {
        return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    private static String text(String bytes) {
        for (int i = 0; i < bytes.length(); i++) {
            if (bytes.charAt(i) > 0xff) {
                return bytes;
            }
        }
        return new String(bytes.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
}
