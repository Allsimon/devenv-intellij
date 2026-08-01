import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// The Gradle distribution of a project that declares one under 'languages.java.gradle'.
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
        // GradleProjectSettings lives in the bundled Gradle plugin, which is also why plugin.xml only
        // depends on it optionally: in an IDE without it, this module stays unloaded and the rest of
        // the plugin keeps working.
        bundledPlugin("com.intellij.gradle")
        testFramework(TestFrameworkType.Platform)
    }
}
