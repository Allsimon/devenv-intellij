# Features

A project is picked up when it has a `devenv.nix` at its root.

- [Nix editing](lsp.md) - `.nix` files are backed by the language server started by `devenv lsp`
- [Processes](processes.md) - The processes declared under `processes` appear in the Services tool window
- [Project SDK](jdk.md) - The Project SDK is set to the JDK devenv declares, and put back when something moves it
- [Gradle](gradle.md) - Linked Gradle builds use the Gradle devenv declares instead of the wrapper's, on the Project SDK
- [Maven](maven.md) - The Maven home path is set to the Maven devenv declares instead of the one bundled with the IDE
- [Node.js](javascript.md) - The Node.js interpreter and package manager are set to the ones devenv declares
- [Reformat Code](treefmt.md) - Delegated to the project's own `treefmt` for the file types it is configured to format
- [`.devenv`](exclusion.md) - The state directory is excluded from indexing and search

The screenshots are captured from a real IDE by `gradle uiTest`.
