# Reformat Code

Delegated to the project's own `treefmt` for the file types it is configured to format.

|                |                       |
| -------------- | --------------------- |
| devenv options | `treefmt.enable`      |
| IDE setting    | Code \| Reformat Code |

The formatter claims a file only when `.devenv/profile/bin/treefmt` exists and some configured formatter covers that file name. Anything else is left to the IDE's own formatters, which is why the failure mode is "nothing happened" rather than an error.

`treefmt --stdin` is used and the document replaced with its output, so unsaved buffers are formatted and nothing is written to disk behind the editor's back.

The `treefmt` that runs is the one of the `devenv.nix` nearest the file, so in a project holding several - modules attached side by side, or a repository whose modules each carry one - a file is formatted by the configuration of the module it belongs to, and the paths it is matched against are relative to that module. A file outside every root is left to the IDE's own formatters.
