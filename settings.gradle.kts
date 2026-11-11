import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "devenv-intellij"

pluginManagement {
    plugins {
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.20.0"
}

dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenCentral()

        // affogato's formatter, built by its flake (the maven-repo package): devenv exports it, the Nix build passes -PaffogatoRepo.
        providers.gradleProperty("affogatoRepo").orElse(providers.environmentVariable("AFFOGATO_MAVEN_REPO")).orNull?.let { repo ->
            maven {
                url = uri(repo)
                content { includeGroup("org.affogato") }
            }
        }

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
