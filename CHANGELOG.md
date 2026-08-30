<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Devenv-intellij Changelog

## [Unreleased]

## [1.0.0] - 2026-08-30

### Added

- Nix files in a project with a `devenv.nix` are now backed by the language server started by `devenv lsp`,
  one server per `devenv.nix` of the project, each answering for the files of its own root.
- The `devenv.yaml` and `devenv.local.yaml` of a project with a `devenv.nix` are now mapped to the JSON schema
  devenv publishes at https://devenv.sh/devenv.schema.json, so they get completion, documentation and
  validation without the `# yaml-language-server: $schema=...` modeline. The IDE downloads the schema once
  and caches it; only IDEs bundling the JSON plugin are concerned, and the YAML plugin is what applies it.
- The processes declared under `processes` in `devenv.nix` now appear in the Services tool window, with their
  status, per-process start/stop/restart, live logs, and `devenv up -d` / `devenv down` on the root node.
  Selecting a process follows the log files the `native` process manager writes, so its output arrives as it
  is produced, with stderr and ANSI colours kept and a filter above the console; any other
  `process.manager.implementation` falls back to the `devenv processes logs` snapshot. A project holding
  several `devenv.nix` - modules attached side by side, or a repository whose modules each carry one -
  contributes the processes of all of them, under a node per root.
- The `.devenv` state directory is now excluded from indexing and search, without modifying the module
  configuration - one per `devenv.nix` of the project, so the modules of a repository carrying several
  each get theirs excluded.
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
  IDE and the `treefmt` git hook agree. Other file types keep using the IDE's built-in formatters. The
  `treefmt` of the `devenv.nix` nearest the file is the one that runs, so each module of a project holding
  several is formatted by its own configuration.
