import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatform.get())
        testFramework(TestFrameworkType.Platform)

        // The bundled plugins the optional features compile against. Each one is only an optional
        // <depends> in plugin.xml, so an IDE that doesn't bundle it leaves the matching
        // configuration file - and the classes it names - unloaded, and the rest of the plugin
        // keeps working.
        //
        // JavaSdk and the SDK table (com.allsimon.intellij.jdk).
        bundledPlugin("com.intellij.java")
        // GradleProjectSettings (com.allsimon.intellij.gradledist).
        bundledPlugin("com.intellij.gradle")
        // MavenGeneralSettings (com.allsimon.intellij.maven).
        bundledPlugin("org.jetbrains.idea.maven")
        // NodeJsInterpreterManager (com.allsimon.intellij.javascript).
        bundledPlugin("JavaScript")
        // The JSON schema machinery (com.allsimon.intellij.schema).
        bundledPlugin("com.intellij.modules.json")
    }
}

// What it takes to get a build to JetBrains Marketplace. The Release workflow, which runs when a
// GitHub release is published, exports all four from the repository secrets; a local 'publishPlugin'
// needs them in the environment just the same. Note that publishPlugin only ever updates a listing
// that already exists - the first version of a plugin has to be uploaded by hand.
intellijPlatform {
    // Signing is optional as far as the Marketplace is concerned, but an unsigned plugin is one the
    // IDE cannot tell apart from a tampered-with copy of it.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
