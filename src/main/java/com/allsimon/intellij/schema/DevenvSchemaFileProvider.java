package com.allsimon.intellij.schema;

import com.allsimon.intellij.core.DevenvCli;
import com.allsimon.intellij.core.MyMessageBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps the 'devenv.yaml' of a devenv project to the schema devenv publishes for it, so that its
 * inputs, imports and options get completion, documentation and validation without the
 * {@code # yaml-language-server: $schema=...} modeline the devenv documentation otherwise asks to
 * paste at the top of every file.
 * <p>
 * The schema is taken from devenv's own site rather than shipped with the plugin: it describes the
 * devenv release a user runs, which moves on its own schedule, and a copy frozen at plugin build time
 * would report new options as errors. The platform downloads it once and keeps it in its remote schema
 * cache, so the mapping keeps working offline afterwards.
 */
final class DevenvSchemaFileProvider implements JsonSchemaFileProvider {
    /** Where devenv publishes the schema, and what its documentation points the modeline at. */
    static final String SCHEMA_URL = "https://devenv.sh/devenv.schema.json";

    private static final String CONFIG_FILE = "devenv.yaml";
    // Read on top of devenv.yaml, with the same shape, and git-ignored: per-developer overrides.
    private static final String LOCAL_CONFIG_FILE = "devenv.local.yaml";

    private final Project project;

    DevenvSchemaFileProvider(@NotNull Project project) {
        this.project = project;
    }

    static boolean isDevenvYaml(@NotNull VirtualFile file) {
        String name = file.getName();
        return CONFIG_FILE.equals(name) || LOCAL_CONFIG_FILE.equals(name);
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
        // Name first: this runs for every file the schema service considers, while findDevenvRoot
        // walks the project's base directories.
        return isDevenvYaml(file)
                && DevenvCli.findDevenvRoot(project) != null
                // Nothing to map the file to when the IDE is not allowed to fetch remote schemas -
                // saying it is available anyway would only claim the file and leave it without one.
                && JsonFileResolver.isRemoteEnabled(project);
    }

    @Override
    public @NotNull String getName() {
        return MyMessageBundle.message("schema.devenv.name");
    }

    @Override
    public @Nullable VirtualFile getSchemaFile() {
        return JsonFileResolver.urlToFile(SCHEMA_URL);
    }

    @Override
    public @NotNull SchemaType getSchemaType() {
        return SchemaType.remoteSchema;
    }

    @Override
    public @NotNull JsonSchemaVersion getSchemaVersion() {
        // What the published schema declares in its own '$schema'. The default is draft 4, under which
        // its '$defs' and 'prefixItems' would go unread.
        return JsonSchemaVersion.SCHEMA_2020_12;
    }

    @Override
    public @NotNull String getRemoteSource() {
        return SCHEMA_URL;
    }
}
