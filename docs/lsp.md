# Nix editing

`.nix` files are backed by the language server started by `devenv lsp`.

|                |                                                        |
| -------------- | ------------------------------------------------------ |
| devenv options | (none - any project with a `devenv.nix`)               |
| IDE setting    | Settings \| Languages & Frameworks \| Language Servers |

The plugin ships no language intelligence of its own. It tells the platform's LSP client to launch `devenv lsp`, which wraps `nixd`, and to hand it every `.nix` file of the project. The server starts when the first such file is opened, not when the project loads.
