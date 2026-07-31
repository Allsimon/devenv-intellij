<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Devenv-intellij Changelog

## [Unreleased]

### Added

- Nix files in a project with a `devenv.nix` are now backed by the language server started by `devenv lsp`.
- The processes declared under `processes` in `devenv.nix` now appear in the Services tool window, with their
  status, per-process start/stop/restart, a log snapshot, and `devenv up -d` / `devenv down` on the root node.
- The `.devenv` state directory is now excluded from indexing and search, without modifying the module
  configuration.
- Reformat Code now runs the project's own `treefmt` for the file types it is configured to format, so the
  IDE and the `treefmt` git hook agree. Other file types keep using the IDE's built-in formatters.
