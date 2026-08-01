import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// The Project SDK pointed at the JDK a devenv project declares under 'languages.java'.
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
        // JavaSdk and the SDK table live in the bundled Java plugin, which is also why plugin.xml
        // only depends on 'com.intellij.modules.java' optionally: in an IDE without it, this whole
        // module stays unloaded and the rest of the plugin keeps working.
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}
