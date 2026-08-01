import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "devenv-intellij"

// One module per feature. Each depends on ':core' and on none of the others; the root project owns
// plugin.xml and composes them all into a single plugin jar.
// ':gradledist' rather than ':gradle': the root already has a 'gradle' directory, holding the wrapper
// and the version catalog.
include(":core", ":gradledist", ":jdk", ":lsp", ":processes", ":treefmt")

pluginManagement {
    plugins {
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenCentral()

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
