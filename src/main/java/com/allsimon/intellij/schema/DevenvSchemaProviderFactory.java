package com.allsimon.intellij.schema;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Registers the schema devenv publishes for 'devenv.yaml' with the platform's JSON schema machinery,
 * which is what backs completion, documentation and validation in YAML files too.
 * <p>
 * The mapping is the only thing the plugin adds: everything a user sees in the editor comes from the
 * schema itself, resolved by the platform.
 */
public final class DevenvSchemaProviderFactory implements JsonSchemaProviderFactory, DumbAware {

    @Override
    public @NotNull List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
        return List.of(new DevenvSchemaFileProvider(project));
    }
}
