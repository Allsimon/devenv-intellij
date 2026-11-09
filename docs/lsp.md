# Nix language server

`.nix` files are also backed by the language server started by `devenv lsp`.

|                |                                                        |
| -------------- | ------------------------------------------------------ |
| devenv options | (none - any project with a `devenv.nix`)               |
| IDE setting    | Settings \| Languages & Frameworks \| Language Servers |

Next to the plugin's own [Nix support](nix.md), the platform's LSP client launches `devenv lsp`, which wraps `nixd`, and hands it the `.nix` files of the root it was started in. The server brings what takes evaluating Nix: completion and documentation of the devenv options, of `pkgs.*` and of `lib.*`, against the nixpkgs pinned by `devenv.lock`. The server starts when the first such file is opened, not when the project loads.

A project can hold several `devenv.nix` - modules attached side by side, or a repository whose modules each carry one - and each one gets a server of its own, started the first time a `.nix` file under it is opened. A file held by two roots is described by the innermost, so exactly one server answers for it, with the devenv options and the nixpkgs of the environment the file actually belongs to. A `.nix` file lying outside every root gets no server: no devenv can say what it means.
