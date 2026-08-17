# Devenv-intellij

An IntelliJ Platform plugin adding support for [devenv](https://devenv.sh) in IntelliJ-based IDEs.
Unofficial and community-maintained; not affiliated with the devenv project.

> [!NOTE]
> 🤖 **Vast quantities of this plugin were written by an AI**, under human supervision, in an IDE,
> about an IDE, inside a reproducible shell that the plugin itself configures. Every line was read
> by a human before it got in — the robot is prolific, not unsupervised. If you find a bug, it is
> statistically likely to be the machine's fault and contractually the human's problem.

## Features

A project is picked up when it has a `devenv.nix` at its root.

| Feature       | Description                                                                                                                                                                                   |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Nix editing   | `.nix` files are backed by the language server started by `devenv lsp`                                                                                                                        |
| Processes     | The processes declared under `processes` appear in the Services tool window, with start/stop/restart and `devenv up -d` / `devenv down`                                                       |
| Project SDK   | With `languages.java.enable`, the Project SDK is set to the JDK devenv declares, and put back whenever something else moves it                                                                |
| Gradle        | With `languages.java.gradle.enable`, linked Gradle builds use the Gradle devenv declares instead of the wrapper's, and run on the Project SDK                                                 |
| Maven         | With `languages.java.maven.enable`, the Maven home path is set to the Maven devenv declares instead of the one bundled with the IDE                                                           |
| Node.js       | With `languages.javascript.enable`, the Node.js interpreter is set to the one devenv declares instead of one found on the machine, along with the `pnpm`, `yarn` or `npm` declared next to it |
| Reformat Code | Delegated to the project's own `treefmt` for the file types it is configured to format, so the IDE and the `treefmt` git hook agree                                                           |
| `.devenv`     | The state directory is excluded from indexing and search, without modifying the module configuration                                                                                          |

The Project SDK, Gradle, Maven and Node.js features need a bundled plugin the IDE may not have; they
are declared optional, so the rest keeps working in IDEs that don't bundle it.

## Plugin structure

The plugin is a single Gradle module, with one Java package per feature. `core` holds what the other
packages share; each feature package depends on it and on none of the others.

```
.
├── .github/                GitHub Workflows, issue templates, and Dependabot configuration
├── .run/                   Predefined Run/Debug Configurations
├── gradle
│   ├── wrapper/            Gradle Wrapper
│   ├── libs.versions.toml  Version catalog (JUnit, IntelliJ Platform version)
├── src
│   ├── main
│   │   ├── java/com/allsimon/intellij/
│   │   │   ├── core/       Locating and invoking the devenv CLI, message bundle, .devenv exclusion
│   │   │   ├── gradle/     Gradle distribution and JVM taken from 'languages.java.gradle' instead of the wrapper
│   │   │   ├── javascript/ Node.js interpreter and package manager taken from 'languages.javascript'
│   │   │   ├── jdk/        Project SDK set to the JDK declared under 'languages.java'
│   │   │   ├── lsp/        Nix language support, backed by 'devenv lsp'
│   │   │   ├── maven/      Maven home path taken from 'languages.java.maven'
│   │   │   ├── processes/  devenv processes in the Services tool window
│   │   │   └── treefmt/    Reformat Code delegated to the project's treefmt
│   │   └── resources/
│   │       ├── META-INF/   plugin.xml, the optional configuration files and pluginIcon.svg
│   │       └── messages/   Message bundle
│   └── test
│       └── java/           Tests, one package per feature
├── build.gradle.kts        Build script
├── devenv.nix              The development environment of the plugin itself
├── gradle.properties       Gradle configuration properties
├── README.md               This file
├── settings.gradle.kts     Gradle project settings
└── TROUBLESHOOTING.md      Diagnosing a feature that doesn't fire, per package
```

Only `core` exposes public API — everything else stays package-private inside its own package.

A new feature means a new package under `com.allsimon.intellij`, and its extension registered in [plugin.xml][file:plugin.xml]. A feature that needs a bundled plugin the IDE may not have goes in one of the optional configuration files instead, next to a `bundledPlugin` dependency in [build.gradle.kts][file:build.gradle.kts] and an optional `<depends>` in `plugin.xml`.

## Development

The repository is itself a devenv project: `devenv shell` provides the JDK and the Gradle the build
expects. Building the plugin is then:

```bash
gradle buildPlugin
```

which writes the installable `build/distributions/devenv-intellij-<version>.zip`. `devenv build
devenv-intellij` produces the same distribution as a Nix output.

The `.run` directory holds the matching Run/Debug configurations.

| Task              | Gradle task                                              |
| ----------------- | -------------------------------------------------------- |
| Run IDE in Plugin | [`:runIde`][docs:runIde], with the _Debug_ icon to debug |
| Run Tests         | [`:check`][gradle:lifecycle-tasks]                       |
| Run Verifications | [`:verifyPlugin`][docs:verifyPlugin]                     |

`runIde` boots a sandbox IDE whose log is at
`.intellijPlatform/sandbox/devenv-intellij/<IDE-build>/log/idea.log` — the first place to look when
a feature doesn't fire. See [TROUBLESHOOTING.md][file:TROUBLESHOOTING.md].

[docs:runIde]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#runIde
[docs:verifyPlugin]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#verifyPlugin
[file:build.gradle.kts]: ./build.gradle.kts
[file:plugin.xml]: ./src/main/resources/META-INF/plugin.xml
[file:TROUBLESHOOTING.md]: ./TROUBLESHOOTING.md
[gradle:lifecycle-tasks]: https://docs.gradle.org/current/userguide/java_plugin.html#lifecycle_tasks
