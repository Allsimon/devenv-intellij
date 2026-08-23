# `devenv.yaml` editing

`devenv.yaml` is mapped to the JSON schema devenv publishes, without the `$schema` modeline.

|                |                                                                               |
| -------------- | ----------------------------------------------------------------------------- |
| devenv options | (none - any project with a `devenv.nix`)                                      |
| IDE setting    | Settings \| Languages & Frameworks \| Schemas and DTDs \| Remote JSON Schemas |

`devenv.yaml` and `devenv.local.yaml` get completion, documentation on hover and validation of their inputs, imports and options. The devenv documentation obtains the same by asking for a `# yaml-language-server: $schema=https://devenv.sh/devenv.schema.json` comment at the top of the file; the plugin registers the mapping instead, so nothing has to be pasted into a project's files.

The schema is fetched from https://devenv.sh/devenv.schema.json rather than shipped with the plugin: it describes the devenv release a user runs, and a copy frozen at plugin build time would report newer options as errors. The IDE downloads it once and serves it from its remote schema cache afterwards, so the mapping survives being offline - but a project opened offline for the first time gets no schema until the download succeeds. Clearing "Allow downloading JSON Schemas from remote sources" in the settings above disables the mapping altogether, and the file is left as plain YAML.

The status bar shows `devenv.yaml` as the schema in effect while such a file is open; the same widget maps another schema by hand if a project wants one. The mapping needs the YAML plugin to reach the editor, and the JSON plugin to exist at all - in an IDE bundling neither, the rest of the plugin is unaffected.
