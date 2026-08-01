import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// The processes declared in devenv.nix, shown and controlled in the Services tool window.
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
