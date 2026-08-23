<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Devenv-intellij Changelog

## [Unreleased]

### Added

- Nix files in a project with a `devenv.nix` are now backed by the language server started by `devenv lsp`.
- The `devenv.yaml` and `devenv.local.yaml` of a project with a `devenv.nix` are now mapped to the JSON schema
  devenv publishes at https://devenv.sh/devenv.schema.json, so they get completion, documentation and
  validation without the `# yaml-language-server: $schema=...` modeline. The IDE downloads the schema once
  and caches it; only IDEs bundling the JSON plugin are concerned, and the YAML plugin is what applies it.
- The processes declared under `processes` in `devenv.nix` now appear in the Services tool window, with their
  status, per-process start/stop/restart, a log snapshot, and `devenv up -d` / `devenv down` on the root node.
- The `.devenv` state directory is now excluded from indexing and search, without modifying the module
  configuration.
- The Project SDK of a project whose `devenv.nix` sets `languages.java.enable` is now set to the JDK that
  `devenv.nix` declares, and put back whenever something else moves it - a Gradle or Maven import assigning
  its own JDK, or a choice made by hand in Project Structure. Only IDEs bundling the Java plugin are
  concerned; the Gradle and Maven JVM settings themselves are left alone.
- A project whose `devenv.nix` sets `languages.java.gradle.enable` now has its linked Gradle builds pointed
  at the Gradle `devenv.nix` declares, instead of the wrapper downloading one of their own, and run on the
  Project SDK, which is the JDK `devenv.nix` declares. Both apply from the next Gradle reload on. Only IDEs
  bundling the Gradle plugin are concerned. A Java toolchain declared by the build is still resolved by
  Gradle itself.
- A project whose `devenv.nix` sets `languages.java.maven.enable` now has its Maven home path set to the
  Maven `devenv.nix` declares, instead of the one bundled with the IDE. It applies from the next Maven
  reload on. Only IDEs bundling the Maven plugin are concerned.
- A project whose `devenv.nix` sets `languages.javascript.enable` now has its Node.js interpreter set to the
  one `devenv.nix` declares, instead of whichever Node.js the IDE finds on the machine, and its package
  manager set to the `pnpm`, `yarn` or `npm` declared next to it - the first of those the project
  enables, since the IDE has a single such setting. Both apply at once, to run configurations and to the
  JavaScript tooling alike. Only IDEs bundling the JavaScript plugin are concerned.
- Reformat Code now runs the project's own `treefmt` for the file types it is configured to format, so the
  IDE and the `treefmt` git hook agree. Other file types keep using the IDE's built-in formatters.
