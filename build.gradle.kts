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

        // 'pluginComposedModule', not 'pluginModule': the former merges a module into the single
        // plugin jar, so the one plugin.xml below can name classes from any module. 'pluginModule'
        // would instead declare a v2 content module, shipped as its own lib/modules/*.jar and only
        // loaded if plugin.xml declares it in a <content> block - without that the IDE silently
        // loads none of these classes and every extension goes missing.
        //
        // Every feature module has to be listed here; a module missing from this list compiles fine
        // and then fails at runtime with a missing extension implementation.
        pluginComposedModule(implementation(project(":core")))
        pluginComposedModule(implementation(project(":gradledist")))
        pluginComposedModule(implementation(project(":jdk")))
        pluginComposedModule(implementation(project(":lsp")))
        pluginComposedModule(implementation(project(":maven")))
        pluginComposedModule(implementation(project(":processes")))
        pluginComposedModule(implementation(project(":treefmt")))
    }
}
