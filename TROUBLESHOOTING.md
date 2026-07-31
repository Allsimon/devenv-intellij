# Troubleshooting the devenv plugin

Two independent features can go wrong: the language server behind `devenv.nix` editing
(sections 1-4), and the devenv processes shown in the Services tool window (section 5).

# Troubleshooting the devenv language server

This plugin doesn't ship its own language intelligence for `devenv.nix` — it just tells the
IntelliJ Platform LSP client to launch `devenv lsp` (which wraps `nixd`) and hand it any `.nix`
file. When "the LSP doesn't seem to do anything" the fault sits in one of three places: the
plugin never asked the platform to start a server, the process failed to start, or it started
but `nixd` isn't producing useful output. The steps below narrow that down using the sandbox
IDE's own log, without needing to add any printlns or attach a debugger.

## 1. Read `idea.log` from the sandbox IDE first

`gradle runIde` boots the plugin inside a disposable sandbox, not your real IDE install. Its log
is the single most useful source of truth and is not shown in the Gradle console output.

```
.intellijPlatform/sandbox/devenv-intellij/<IDE-build>/log/idea.log
```

Tail it while `runIde` is running, or grep it after the fact:

```bash
grep -n 'DevenvLspServerDescriptor' .intellijPlatform/sandbox/devenv-intellij/IU-*/log/idea.log
```

A **healthy** startup looks like this (four lines, in order):

```
INFO - #c.i.p.l.i.LspServerImpl - DevenvLspServerDescriptor@<project>(Initializing;0): Starting LSP server
INFO - #c.i.p.l.a.LspServerDescriptor - DevenvLspServerDescriptor@<project>: starting LSP server: /nix/store/.../devenv [lsp]
INFO - #c.i.p.l.i.LspServerImpl - DevenvLspServerDescriptor@<project>(Initializing;0): LSP server process started: .../devenv lsp
INFO - #c.i.p.l.i.LspServerImpl - DevenvLspServerDescriptor@<project>(Running;0): LSP server initialized in 0.259s, name = nixd, version = 2.9.2
```

After that, every completion/hover/diagnostic request and reply is logged as
`STDERR: I[...] <-- textDocument/...` / `--> reply:...` lines — that's `nixd`'s own trace output,
forwarded verbatim into `idea.log` by the platform. If you see those lines with matching
`<--`/`-->` pairs, the server is genuinely working end to end; the problem is more likely
something not refreshing in the editor UI than the LSP itself.

If you see **no `DevenvLspServerDescriptor` lines at all**, the plugin never even tried to start
a server — skip to section 2. If you see the first line or two but not "LSP server initialized",
the process failed to launch or crashed — see section 3.

## 2. Nothing in the log: the server was never started

`fileOpened` in
[`DevenvLspServerSupportProvider`][file:DevenvLspServerSupportProvider] only calls
`ensureServerStarted` when **both** of these hold:

- The opened file has extension `.nix` and lives on the local filesystem
  (`DevenvLspServerDescriptor.isNixFile`) — not a diff view, not a light/in-memory file.
- `devenv.nix` exists directly inside one of the project's **content roots**
  (`BaseProjectDirectories.getBaseDirectories`, checked by `findDevenvRoot`). This is **not**
  a recursive search — a `devenv.nix` in a parent or a nested subdirectory won't be found.

Checklist:

- [ ] In the sandbox IDE, open a project whose root directory contains `devenv.nix` directly
      (`File | Open` on that directory, not a subdirectory of it).
- [ ] Open (or click into) an actual `.nix` file from that project — the server only starts
      reactively per file-open event, not eagerly when the project loads. Closing and reopening
      the file after the IDE has fully indexed the project is a good way to force a retry.
- [ ] Confirm the plugin itself loaded: `Settings | Plugins | Installed`, search "Devenv", make
      sure there's no error banner. Also check for a `PluginManager - Problems found loading
plugins` block near the top of `idea.log` and confirm `com.allsimon.devenv` isn't in it.
- [ ] Confirm the sandbox IDE edition actually bundles the LSP client module the plugin depends
      on (`<depends>com.intellij.modules.lsp</depends>` in
      [`plugin.xml`][file:plugin.xml]). The `IU-*` sandbox directory name means IntelliJ IDEA
      **Ultimate**, which has it; if this ever gets pointed at a Community target the module
      won't exist and the plugin won't load at all (that failure shows up as a
      `PluginManager` error, not silence).

## 3. Log stops after "starting LSP server" / process never initializes

This means `GeneralCommandLine` failed to launch `devenv`, or `devenv lsp` exited immediately.

- **`Cannot find 'devenv' in PATH`** (this plugin's own error, from
  [`MyMessageBundle.properties`][file:MyMessageBundle.properties],
  surfaced as a notification and in the log): `PathEnvironmentVariableUtil.findInPath` resolves
  against the **PATH of the JVM running the sandbox IDE**, i.e. whatever environment `gradle
runIde` itself inherited — not your interactive shell's `PATH` if `runIde` was launched some
  other way (IDE "Run" button with a stale run configuration, a cron/CI job, a GUI launcher).
  Fix: always launch `gradle runIde` from inside `devenv shell` in this repo, so `devenv` (and
  the pinned `JETBRAINS_RUNTIME`, per the comment at the top of
  [`devenv.nix`][file:devenv.nix]) are on `PATH` before Gradle ever starts.
- **Process started but exited right away**: look for `STDERR` lines immediately after "LSP
  server process started" — `devenv lsp` prints its own errors there (e.g. it couldn't evaluate
  `devenv.nix`, missing `devenv.lock`, or the project isn't a valid devenv project at all). Those
  come straight from the `devenv`/`nixd` CLI, so reproducing `devenv lsp` by hand in a terminal
  _inside the target project's directory_ (the one containing its `devenv.nix`, not this plugin's
  repo) is the fastest way to see the real error without the IDE in the loop.
- Since `createCommandLine` sets `ParentEnvironmentType.CONSOLE`, the spawned `devenv lsp`
  process gets the sandbox IDE's inherited console environment (Nix profile variables, etc.) —
  if that's wrong, it'll typically fail the same way a plain terminal invocation of `devenv lsp`
  would from the same shell.

## 4. Server initializes, but no completions/diagnostics/hover show up in the editor

If section 1's healthy four-line sequence _is_ present and you also see paired
`<-- textDocument/...` / `--> reply:...` traffic while typing, the server is working — this is
now a platform/UI symptom rather than an LSP wiring bug. Things worth checking:

- Bottom-right status bar of the sandbox IDE: there's an LSP servers widget (small icon) that
  lists connected servers per file; click it to see status (`Running`/`Stopped`/`Failed`) and a
  "stop/restart" action, without needing to restart the whole sandbox.
  `Settings | Languages & Frameworks | Language Servers` shows the same list persistently.
- If you see `<-- textDocument/didOpen` but never any `<-- textDocument/completion` etc. even
  after typing and invoking completion manually (`Ctrl+Space` / `Cmd+Space`), the file may not be
  the one the server thinks is open, or caret/typing events aren't reaching the editor at all —
  try a fresh, simple `.nix` file rather than a large/generated one first.
- Occasional `No response from the server in 300ms for: ...HoverResultCache...` INFO lines are
  normal (nixd is just slow to answer a hover in time for the platform's cache) and not evidence
  of a broken setup by themselves.
- If `nixd` replies with an `E[...] ... failed: ...` line for a specific request (e.g.
  `textDocument/documentHighlight failed: cannot find variable on given node`), that's `nixd`
  reporting it can't resolve that specific document position — normal for e.g. clicking on a
  syntax token it doesn't model, not a sign the server is broken.

## 5. No `devenv` node in the Services tool window

The Services node is built by [`DevenvServiceViewContributor`][file:DevenvServiceViewContributor]
from two devenv commands, run by
[`DevenvProcessManager`][file:DevenvProcessManager] on a background poll every 5 seconds:
`devenv eval processes` for the declared processes and `devenv processes list` for their status.
The platform hides a contributor whose service list is empty, so **an absent node means an empty
list**, not a crash.

- The node only appears for projects whose **content root** contains `devenv.nix` — the same
  `findDevenvRoot` rule as section 2, now in `DevenvCli`. It also needs `devenv` on the PATH of the
  IDE process (section 3).
- A devenv project with no `processes` in its `devenv.nix` legitimately shows nothing. Confirm with
  `devenv eval processes` in a terminal: an empty `{"processes": {}}` means there is nothing to show.
- Newly declared processes appear after the plugin re-evaluates `devenv.nix`, which happens when the
  file changes on disk or when you press **Reload from devenv.nix** on the root node. The first
  evaluation after a cold nix eval cache can take minutes; it runs under a "Loading devenv processes"
  background progress indicator.
- Status comes from `devenv processes list`, which devenv exposes only as a human-readable table with
  no `--json` mode and no compatibility promise. If a devenv upgrade changes that table, statuses show
  up as `unknown` while the names stay correct — the format is pinned by `DevenvProcessParserTest`, so
  running the tests is the fastest way to confirm a format change.
- `grep -n 'DevenvProcessManager' .intellijPlatform/sandbox/devenv-intellij/IU-*/log/idea.log` shows
  failing commands; failures of an explicit action (Start, Stop, …) are also reported as a balloon.

## Quick reference

| Symptom in `idea.log`                                          | Likely cause                                                  |
| -------------------------------------------------------------- | ------------------------------------------------------------- |
| No `DevenvLspServerDescriptor` lines at all                    | Wrong project root, wrong file type, or plugin didn't load    |
| `Cannot find 'devenv' in PATH`                                 | `runIde` launched outside `devenv shell`                      |
| "Starting LSP server" then nothing / immediate `STDERR` error  | `devenv lsp` itself failed — reproduce it directly in a shell |
| Full four-line healthy sequence + paired request/reply traffic | Server works; look at the editor/UI side, not the LSP wiring  |
| No `devenv` node in Services at all                            | No `devenv.nix` at a content root, or no `processes` declared |

[file:build.gradle.kts]: ./build.gradle.kts
[file:plugin.xml]: ./src/main/resources/META-INF/plugin.xml
[file:devenv.nix]: ./devenv.nix
[file:DevenvLspServerSupportProvider]: ./src/main/java/com/allsimon/intellij/DevenvLspServerSupportProvider.java
[file:DevenvServiceViewContributor]: ./src/main/java/com/allsimon/intellij/DevenvServiceViewContributor.java
[file:DevenvProcessManager]: ./src/main/java/com/allsimon/intellij/DevenvProcessManager.java
[file:MyMessageBundle.properties]: ./src/main/resources/messages/MyMessageBundle.properties
