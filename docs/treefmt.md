# Reformat Code

Delegated to the project's own `treefmt` for the file types it is configured to format.

|                |                       |
| -------------- | --------------------- |
| devenv options | `treefmt.enable`      |
| IDE setting    | Code \| Reformat Code |

The formatter claims a file only when `.devenv/profile/bin/treefmt` exists and some configured formatter covers that file name. Anything else is left to the IDE's own formatters, which is why the failure mode is "nothing happened" rather than an error.

`treefmt --stdin` is used and the document replaced with its output, so unsaved buffers are formatted and nothing is written to disk behind the editor's back.
