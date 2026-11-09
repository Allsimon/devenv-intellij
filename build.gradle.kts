import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

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
    implementation(libs.affogato.format)
    implementation(libs.affogato.lint)
    testImplementation(libs.junit)
    // Parameterized tests run on JUnit 5; the vintage engine keeps running the JUnit 3/4 platform tests.
    testImplementation(platform("org.junit:junit-bom:${libs.versions.junit5.get()}"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage)
    testRuntimeOnly(libs.junit.launcher)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatform.get())
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)

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
        // The Shell Script language the Nix injector looks up by id, only for the tests: the plugin needs no class of it.
        bundledPlugin("com.jetbrains.sh")
        // LanguageInjectionSupport, which keeps "Inject language or reference" as a comment in Nix (com.allsimon.intellij.nixlang).
        bundledModule("intellij.platform.langInjection")
    }
}


intellijPlatform {
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

// The affogato jars come read-only from the Nix store; a sandbox copy keeping that mode can't be overwritten by the next one.
tasks.withType<PrepareSandboxTask>().configureEach {
    filePermissions { user { write = true } }
}

changelog {
    repositoryUrl = "https://github.com/Allsimon/devenv-intellij"
    versionPrefix = ""
}

tasks.test {
    useJUnitPlatform()
}
