package com.allsimon.intellij.treefmt;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.DevenvExcludePolicy;
import com.allsimon.intellij.core.DevenvFeature;
import com.allsimon.intellij.core.DevenvSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The treefmt a devenv project is configured to format itself with.
 * <p>
 * devenv puts a wrapper script at '.devenv/profile/bin/treefmt' that runs the real binary with the
 * generated config and the project's tree root already baked in - the very same command the
 * treefmt git hook runs. Invoking that wrapper is therefore the only way to be sure the IDE formats
 * a file exactly as a commit would.
 */
final class DevenvTreefmt {
    private static final Logger LOG = Logger.getInstance(DevenvTreefmt.class);

    private static final String PROFILE_PATH = DevenvCli.STATE_DIRECTORY + "/profile";
    private static final String TREEFMT_PATH = PROFILE_PATH + "/bin/treefmt";

    /**
     * Keyed by devenv root, valid while {@link Resolved#profile} still matches. The profile symlink
     * points into the Nix store and changes whenever the environment is rebuilt, so comparing it is
     * both a cheap staleness check and the only invalidation this needs.
     */
    private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<>();

    private final Path executable;
    private final Path devenvRoot;
    private final TreefmtConfig config;

    private DevenvTreefmt(@NotNull Path executable, @NotNull Path devenvRoot, @NotNull TreefmtConfig config) {
        this.executable = executable;
        this.devenvRoot = devenvRoot;
        this.config = config;
    }

    @NotNull Path executable() {
        return executable;
    }

    @NotNull Path devenvRoot() {
        return devenvRoot;
    }

    /**
     * The treefmt of {@code project}, or {@code null} when there isn't one - the feature is switched
     * off, the project isn't a devenv project, 'devenv shell' has never built its profile, or treefmt
     * isn't enabled. Callers are expected to fall back to the IDE's own formatting in that case.
     */
    static @Nullable DevenvTreefmt resolve(@NotNull Project project) {
        if (!DevenvSettings.getInstance().isEnabled(DevenvFeature.TREEFMT)) {
            return null;
        }

        VirtualFile root = DevenvCli.findDevenvRoot(project);
        if (root == null) {
            return null;
        }

        Path devenvRoot = Path.of(root.getPath());
        Path profile = readProfileLink(devenvRoot);
        if (profile == null) {
            CACHE.remove(devenvRoot.toString());
            return null;
        }

        Resolved cached = CACHE.get(devenvRoot.toString());
        if (cached != null && cached.profile.equals(profile)) {
            return cached.treefmt;
        }

        DevenvTreefmt treefmt = read(devenvRoot);
        CACHE.put(devenvRoot.toString(), new Resolved(profile, treefmt));
        return treefmt;
    }

    /**
     * Where '.devenv/profile' currently points, used as the cache key. Read with plain NIO rather
     * than through the VFS: '.devenv' is excluded from the index by {@link DevenvExcludePolicy}, and
     * this runs on the formatting hot path.
     */
    private static @Nullable Path readProfileLink(@NotNull Path devenvRoot) {
        try {
            return Files.readSymbolicLink(devenvRoot.resolve(PROFILE_PATH));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static @Nullable DevenvTreefmt read(@NotNull Path devenvRoot) {
        Path executable = devenvRoot.resolve(TREEFMT_PATH);
        if (!Files.isExecutable(executable)) {
            // Normal for a project that doesn't enable treefmt: the profile exists, the wrapper doesn't.
            return null;
        }

        try {
            String configFile = TreefmtConfig.configFilePath(Files.readString(executable));
            if (configFile == null) {
                LOG.warn("No --config-file argument in the treefmt wrapper at " + executable);
                return null;
            }
            TreefmtConfig config = TreefmtConfig.parse(Files.readString(Path.of(configFile)));
            return config.isEmpty() ? null : new DevenvTreefmt(executable, devenvRoot, config);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to read the treefmt configuration of " + devenvRoot, e);
            return null;
        }
    }

    /**
     * Whether treefmt is configured to format {@code file}, which must live under the devenv root.
     */
    boolean canFormat(@NotNull Path file) {
        if (!file.startsWith(devenvRoot)) {
            return false;
        }
        return config.matches(devenvRoot.relativize(file).toString(), file.getFileName().toString());
    }

    private record Resolved(@NotNull Path profile, @Nullable DevenvTreefmt treefmt) {
    }
}
