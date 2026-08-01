import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// Everything the feature modules share: locating and invoking the devenv CLI, the message bundle,
// and the '.devenv' exclusion. Depends on no other module of this plugin.
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
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatform.get())
        testFramework(TestFrameworkType.Platform)
    }
}
