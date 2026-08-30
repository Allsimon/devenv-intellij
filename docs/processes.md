# Processes

The processes declared under `processes` appear in the Services tool window.

|                |                          |
| -------------- | ------------------------ |
| devenv options | `processes`              |
| IDE setting    | The Services tool window |

The declared processes are read with `devenv eval processes` and their status with `devenv processes list`, polled in the background. Each one can be started, stopped and restarted on its own, and `devenv up -d` / `devenv down` act on all of them. Turning on `Group Services by Type` in the tool window puts them under a `Devenv` node that carries those two commands; with it off they are listed at the top level.

Selecting a process streams its output live, stdout in the console's normal colour and stderr in red, with ANSI colours decoded. The console is the platform's log console, so it carries a text filter and a stream selector above it. Restarting a process replaces what it shows rather than appending to it, because the log it is reading has been rewritten.

The output is read from the files the process manager writes under `processes/logs` in the directory `devenv eval devenv.runtime` reports, rather than by re-running `devenv processes logs`, which only ever returns a snapshot. The `.devenv/run` symlink points at the same place most of the time but is not kept up to date, so it is not what the plugin follows.

Those files are an internal layout of the `native` process manager, so a project setting `process.manager.implementation` to anything else falls back to the snapshot, refreshed on selection and on demand.

A project can hold several `devenv.nix` - modules attached side by side, or a repository whose modules each carry one - and every one of them is searched for, in the project's base directories and in the content roots of its modules. devenv runs one process manager per environment, so each root polls, starts and stops on its own, and its processes are listed under a node named after the directory the `devenv.nix` is in. A project with a single `devenv.nix` gets no such node, and the commands on the `Devenv` node are run in every root.

A devenv project that declares no process shows no node at all: the platform hides a contributor whose list is empty.

![Processes](img/processes.jpg)
