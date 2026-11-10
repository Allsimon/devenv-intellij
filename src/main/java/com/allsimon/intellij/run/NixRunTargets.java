package com.allsimon.intellij.run;

import com.allsimon.intellij.nixlang.NixElementTypes;
import com.allsimon.intellij.nixlang.NixFile;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.affogato.parser.NodeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** What the gutter offers at a token: commands to run, or a process to show. */
public final class NixRunTargets {
    /** A command, {@code tool arguments}. */
    public record Command(NixRunConfiguration.Tool tool, String arguments) {
        public String label() {
            return tool.executable() + " " + arguments;
        }
    }

    private static final Set<String> OUTPUTS = Set.of("packages", "apps", "checks");

    private NixRunTargets() {
    }

    /** The commands the gutter offers at {@code leaf}: at the start of a devenv.nix, or at a flake output's name. */
    public static List<Command> commands(@NotNull PsiElement leaf) {
        PsiFile file = leaf.getContainingFile();
        if (!(file instanceof NixFile)) {
            return List.of();
        }
        if (file.getName().equals("devenv.nix") && leaf == firstToken(file)) {
            return List.of(new Command(NixRunConfiguration.Tool.DEVENV, "up"),
                    new Command(NixRunConfiguration.Tool.DEVENV, "test"));
        }
        if (!file.getName().equals("flake.nix")) {
            return List.of();
        }
        List<String> names = namesAt(leaf);
        if (names == null) {
            return List.of();
        }
        String input = input(names);
        if (input != null) {
            return List.of(new Command(NixRunConfiguration.Tool.NIX, "flake update " + input));
        }
        int output = -1;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i) != null && OUTPUTS.contains(names.get(i))) {
                output = i;
            }
        }
        if (output < 0) {
            return List.of();
        }
        List<String> rest = names.subList(output + 1, names.size());
        boolean named = rest.size() == 1 || rest.size() == 2 && isSystem(rest.getFirst());
        if (!named || rest.getLast() == null) {
            return List.of();
        }
        String name = rest.getLast();
        return switch (names.get(output)) {
            case "apps" -> List.of(new Command(NixRunConfiguration.Tool.NIX, "run .#" + name));
            case "checks" -> List.of(new Command(NixRunConfiguration.Tool.NIX, "flake check"));
            default -> List.of(new Command(NixRunConfiguration.Tool.NIX, "build .#" + name));
        };
    }

    /** The input of a flake.nix whose name {@code leaf} is, as in {@code inputs.foo.url}, or null. */
    public static @Nullable String input(@NotNull PsiElement leaf) {
        PsiFile file = leaf.getContainingFile();
        List<String> names = file != null && file.getName().equals("flake.nix") ? namesAt(leaf) : null;
        return names == null ? null : input(names);
    }

    private static @Nullable String input(List<String> names) {
        return names.size() == 2 && "inputs".equals(names.getFirst()) ? names.getLast() : null;
    }

    /** The process {@code processes.<name>} whose name {@code leaf} is, or null. */
    public static @Nullable String process(@NotNull PsiElement leaf) {
        List<String> names = namesAt(leaf);
        if (names == null) {
            return null;
        }
        int processes = names.lastIndexOf("processes");
        return processes >= 0 && processes == names.size() - 2 ? names.getLast() : null;
    }

    /**
     * The names from the outermost binding down to the attribute {@code leaf} names, null for one that is not
     * constant; null when {@code leaf} is not an attribute name of a binding's path.
     */
    private static @Nullable List<String> namesAt(PsiElement leaf) {
        PsiElement attr = leaf.getParent();
        PsiElement path = attr == null ? null : attr.getParent();
        PsiElement binding = path == null ? null : path.getParent();
        if (!is(attr, NodeKind.ATTR_NAME) || !is(path, NodeKind.ATTR_PATH) || !is(binding, NodeKind.BINDING)) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (PsiElement name = path.getFirstChild(); name != null; name = name.getNextSibling()) {
            if (is(name, NodeKind.ATTR_NAME) || is(name, NodeKind.DYNAMIC_ATTR) || is(name, NodeKind.STRING)) {
                names.add(is(name, NodeKind.ATTR_NAME) ? name.getText() : null);
                if (name == attr) {
                    break;
                }
            }
        }
        for (PsiElement e = binding.getParent(); e != null && !(e instanceof PsiFile); e = e.getParent()) {
            if (is(e, NodeKind.BINDING)) {
                ASTNode outerPath = e.getNode().findChildByType(NixElementTypes.of(NodeKind.ATTR_PATH));
                PsiElement outer = outerPath == null ? null : outerPath.getPsi();
                List<String> prefix = new ArrayList<>();
                for (PsiElement name = outer == null ? null : outer.getFirstChild(); name != null;
                     name = name.getNextSibling()) {
                    if (is(name, NodeKind.ATTR_NAME) || is(name, NodeKind.DYNAMIC_ATTR) || is(name, NodeKind.STRING)) {
                        prefix.add(is(name, NodeKind.ATTR_NAME) ? name.getText() : null);
                    }
                }
                names.addAll(0, prefix);
            }
        }
        return names;
    }

    private static boolean isSystem(@Nullable String name) {
        return name == null || name.endsWith("-linux") || name.endsWith("-darwin");
    }

    private static @Nullable PsiElement firstToken(PsiFile file) {
        PsiElement leaf = PsiTreeUtil.getDeepestFirst(file);
        while (leaf instanceof PsiWhiteSpace || leaf instanceof PsiComment) {
            leaf = PsiTreeUtil.nextLeaf(leaf);
        }
        return leaf;
    }

    private static boolean is(@Nullable PsiElement element, NodeKind kind) {
        ASTNode node = element == null ? null : element.getNode();
        return node != null && node.getElementType() == NixElementTypes.of(kind);
    }
}
