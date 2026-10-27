# Nix editing

`.nix` files are backed by the nixd language server configured for devenv.

|                |                                                        |
| -------------- | ------------------------------------------------------ |
| devenv options | (none - any project with a `devenv.nix`)               |
| IDE setting    | Settings \| Languages & Frameworks \| Language Servers |

The plugin ships no language intelligence of its own. It asks `devenv lsp --print-config` for the nixd configuration and gives that configuration to nixd through LSP. It starts a `nixd` found on `PATH` directly. If none is found, it falls back to `devenv lsp`, which bundles nixd. The server starts when the first matching file is opened, not when the project loads.

A project can hold several `devenv.nix` - modules attached side by side, or a repository whose modules each carry one - and each one gets a server of its own, started the first time a `.nix` file under it is opened. A file held by two roots is described by the innermost, so exactly one server answers for it, with the devenv options and the nixpkgs of the environment the file actually belongs to. A `.nix` file lying outside every root gets no server: no devenv can say what it means.
