# `.devenv`

The state directory is excluded from indexing and search.

|                |                                                           |
| -------------- | --------------------------------------------------------- |
| devenv options | (none - any project with a `devenv.nix`)                  |
| IDE setting    | (none - applied through a directory index exclude policy) |

`.devenv` holds nothing a developer edits: per-session shell scripts, an eval cache, and symlinks pointing into the Nix store and into the runtime directory. Indexing a symlink means indexing whatever it resolves to, so leaving the directory in means pulling a whole Nix profile - every package of the environment, with its sources and its own dependencies - into the project's index. That inflates indexing time on every start, and fills Find in Files, Go to File and Go to Symbol with store paths that shadow the project's own results.

One per root: a project holding several `devenv.nix` - modules attached side by side, or a repository whose modules each carry one - gets the `.devenv` of every one of them excluded, since each root builds a state directory of its own.

Excluded without modifying the module configuration, so nothing appears in the project's `.iml` and the exclusion cannot be committed by accident. The flip side is that it cannot be undone from the project tree.

See https://github.com/JetBrains/intellij-community/pull/2171
