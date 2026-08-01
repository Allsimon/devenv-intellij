import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// Nix language support, backed by the server 'devenv lsp' starts.
plugins {
    id("java")
    id("org.jetbrains.intellij.platform.module")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatform.get())
        testFramework(TestFrameworkType.Platform)
    }
}
