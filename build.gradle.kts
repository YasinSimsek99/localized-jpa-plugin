plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.localizedjpa"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)

        // Add plugin dependencies for compilation here:
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.idea.maven")  // For MavenRunner API
    }
    
    // Test dependencies - JUnit 4 for simple unit tests
    testImplementation("junit:junit:4.13")
}

intellijPlatform {
    pluginConfiguration {
        name = "LocalizedJPA"
        description = """
            <p>Provides IntelliJ IDEA support for the LocalizedJPA annotation processor.</p>
            
            <h3>Features</h3>
            <ul>
                <li>Code completion for localized getter/setter methods (e.g., <code>getName(Locale)</code>, <code>setName(String, Locale)</code>)</li>
                <li>Automatic detection of LocalizedJPA library in project dependencies</li>
                <li>One-click annotation processing configuration</li>
                <li>Recognition of synthetic methods generated at compile-time</li>
                <li>Support for <code>@Localized</code> fields and translations map accessors</li>
            </ul>
            
            <h3>Getting started</h3>
            <p>Add the LocalizedJPA library to your Maven or Gradle project. The plugin will automatically detect the library and prompt you to enable annotation processing if needed. Annotate entity fields with <code>@Localized</code> to enable IDE autocomplete for localized methods.</p>
            
            <p>For more information, visit the <a href="https://github.com/YasinSimsek99/localized-jpa">LocalizedJPA documentation</a>.</p>
        """.trimIndent() as String
        
        vendor {
            name = "LocalizedJPA"
            email = "yasinsimsekk67@gmail.com"
            url = "https://github.com/YasinSimsek99/localized-jpa-plugin"
        }
        
        ideaVersion {
            sinceBuild = "252"  // IntelliJ IDEA 2025.2
            // untilBuild removed for forward compatibility (recommended for 2024.3+)
        }

        changeNotes = """
            <h3>0.1.1 - Initial Release</h3>
            <ul>
                <li><b>PSI Augment Provider</b> - Full autocomplete support for @LocalizedEntity synthetic methods</li>
                <li><b>Localized Getters/Setters</b> - Recognition of getName(Locale) and setName(String, Locale) methods</li>
                <li><b>Translations Field</b> - Support for injected translations map field and accessors</li>
                <li><b>Smart Detection</b> - Automatic LocalizedJPA library detection on project open</li>
                <li><b>Auto Configuration</b> - One-click annotation processing setup</li>
            </ul>
        """.trimIndent()
    }

    instrumentCode = false  // Disable instrumentation to avoid JDK path issues
    
    pluginVerification {
        ides {
            recommended()  // Verifies against recommended IDE versions based on sinceBuild/untilBuild
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"  // Required by IntelliJ Platform 2025.2+
        targetCompatibility = "21"
    }
    
    // Configure test task for IntelliJ Platform tests
    test {
        useJUnit()
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
