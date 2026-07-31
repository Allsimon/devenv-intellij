<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Devenv-intellij Changelog

## [Unreleased]

### Added

- Nix files in a project with a `devenv.nix` are now backed by the language server started by `devenv lsp`.
- The processes declared under `processes` in `devenv.nix` now appear in the Services tool window, with their
  status, per-process start/stop/restart, a log snapshot, and `devenv up -d` / `devenv down` on the root node.
