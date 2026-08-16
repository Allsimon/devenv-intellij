package com.allsimon.intellij.treefmt;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which files a project's treefmt is configured to format, read from the treefmt.toml that devenv
 * generates into the Nix store.
 * <p>
 * Kept free of any file or process access, so the two formats it reads can be pinned down by unit
 * tests.
 */
final class TreefmtConfig {
    /**
     * The devenv-generated wrapper runs the real treefmt with its config baked in.
     */
    private static final Pattern CONFIG_FILE_ARGUMENT = Pattern.compile("--config-file\\s+(\\S+)");

    // treefmt.toml only ever puts 'includes' under a '[formatter.<name>]' section, while 'excludes'
    // appears both there and at the document root, where it applies to every formatter. Since this
    // class folds all formatters together anyway (see the class doc of matches()), collecting every
    // occurrence regardless of section gives exactly the union we want, without tracking sections.
    private static final Pattern INCLUDES = Pattern.compile("includes\\s*=\\s*\\[([^]]*)]");
    private static final Pattern EXCLUDES = Pattern.compile("excludes\\s*=\\s*\\[([^]]*)]");
    private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]*)\"");

    private final List<Pattern> includes;
    private final List<Pattern> excludes;

    private TreefmtConfig(@NotNull List<Pattern> includes, @NotNull List<Pattern> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    /**
     * The {@code --config-file} path baked into the devenv treefmt wrapper script.
     */
    static @Nullable String configFilePath(@NotNull String wrapperScript) {
        Matcher matcher = CONFIG_FILE_ARGUMENT.matcher(wrapperScript);
        return matcher.find() ? matcher.group(1) : null;
    }

    static @NotNull TreefmtConfig parse(@NotNull String toml) {
        return new TreefmtConfig(compileGlobs(INCLUDES, toml), compileGlobs(EXCLUDES, toml));
    }

    private static @NotNull List<Pattern> compileGlobs(@NotNull Pattern arrayPattern, @NotNull String toml) {
        List<Pattern> patterns = new ArrayList<>();
        Matcher arrays = arrayPattern.matcher(toml);
        while (arrays.find()) {
            Matcher values = QUOTED_VALUE.matcher(arrays.group(1));
            while (values.find()) {
                patterns.add(Pattern.compile(FileUtil.convertAntToRegexp(values.group(1))));
            }
        }
        return patterns;
    }

    /**
     * Whether treefmt configures no formatter at all, in which case there is nothing to take over.
     */
    boolean isEmpty() {
        return includes.isEmpty();
    }

    /**
     * Whether treefmt would format this file.
     * <p>
     * Every formatter's globs are folded into one set: the question here is only "does treefmt own
     * this file", not "which formatter gets it". A per-formatter {@code excludes} entry therefore
     * excludes a file from all of them - those are empty in practice, and getting it wrong that way
     * only costs a treefmt run that passes the file through unchanged.
     * <p>
     * Globs are matched against the tree-relative path <em>and</em> the bare file name, because
     * treefmt's own {@code *.nix} reaches nested files while an Ant-style path match would stop at
     * the first separator.
     */
    boolean matches(@NotNull String relativePath, @NotNull String fileName) {
        return anyMatch(includes, relativePath, fileName) && !anyMatch(excludes, relativePath, fileName);
    }

    private static boolean anyMatch(@NotNull List<Pattern> patterns, @NotNull String relativePath,
                                    @NotNull String fileName) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(relativePath).matches() || pattern.matcher(fileName).matches()) {
                return true;
            }
        }
        return false;
    }
}
