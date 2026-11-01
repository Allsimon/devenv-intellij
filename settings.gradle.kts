import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "devenv-intellij"

pluginManagement {
    plugins {
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
}

dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenCentral()

        // graalnix's formatter, built by its flake (the maven-repo package): devenv exports it, the Nix build passes -PgraalnixRepo.
        providers.gradleProperty("graalnixRepo").orElse(providers.environmentVariable("GRAALNIX_MAVEN_REPO")).orNull?.let { repo ->
            maven {
                url = uri(repo)
                content { includeGroup("org.graalnix") }
            }
        }

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
