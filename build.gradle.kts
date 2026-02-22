plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.localizedjpa"
version = "0.1.5"

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
        bundledPlugin("org.jetbrains.kotlin")      // For Kotlin PSI API
    }
    
    // Test dependencies - JUnit 4 for simple unit tests
    testImplementation("junit:junit:4.13")
    
    // jOOQ for DDL SQL Generation
    implementation("org.jooq:jooq:3.19.11")
}

intellijPlatform {
    pluginConfiguration {
        name = "LocalizedJPA"
        description = """
            <p>Provides IntelliJ IDEA support for the LocalizedJPA annotation processor.</p>
            
            <h3>Features</h3>
            <ul>
                <li><b>SQL Migration Generator</b>: Generate Flyway DDL scripts directly from <code>@LocalizedEntity</code> classes via IDE action or gutter icon.</li>
                <li>Code completion for both default methods (e.g., <code>getName()</code>, <code>setName(String)</code>) and locale-aware methods (e.g., <code>getName(Locale)</code>, <code>setName(String, Locale)</code>)</li>
                <li>Automatic detection of LocalizedJPA library in project dependencies</li>
                <li>One-click annotation processing configuration</li>
                <li>Recognition of synthetic methods generated at compile-time</li>
                <li>Support for <code>@Localized</code> fields and translations map accessors</li>
            </ul>
            
            <br/>
            <blockquote>
                <b>⚠️ Migration Generator Requires LocalizedJPA 0.1.5+</b><br/>
                To use the SQL Migration Generator without issues, your project must have 
                <b>LocalizedJPA 0.1.5 or newer</b> installed:
                <pre>implementation 'com.localizedjpa:localized-jpa:0.1.5'</pre>
            </blockquote>
            <br/>
            
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
            sinceBuild = "241"  // IntelliJ IDEA 2024.1
            // untilBuild removed for forward compatibility (recommended for 2024.3+)
        }

        changeNotes = """
            <h3>0.1.5 - Release</h3>
            <ul>
                <li><b>SQL Migration Generator</b> - Right-click on a Localized Entity or use <code>Alt+Insert</code> to instantly generate Flyway SQL migrations for translation tables!</li>
                <li><b>Smart Diff Engine</b> - Automatically scans existing <code>db/migration</code> SQL files to find missing fields and switch between <code>CREATE TABLE</code> or <code>ALTER TABLE</code>.</li>
                <li><b>Migration Preview Dialog</b> - View generated SQL strings live using integrated jOOQ before writing to file, with Dialect and custom target directory support.</li>
                <li><b>Auto-Detection</b> - Automatically detects Database Dialect and Flyway locations from <code>application.yml</code>.</li>
            </ul>

            <h3>0.1.4 - Migration Base</h3>
            <ul>
                <li>Initial groundwork for the migration generator.</li>
            </ul>

            <h3>0.1.3 - Dynamic Detection</h3>
            <ul>
                <li><b>Dynamic Library Detection</b> - Instant notification when LocalizedJPA library is added to project</li>
                <li><b>Smarter Configuration</b> - Improved prompts for enabling annotation processing</li>
            </ul>

            <h3>0.1.2 - Enhanced Method Support</h3>
            <ul>
                <li><b>Default Getter/Setter</b> - Added getName() and setName(String) method generation</li>
                <li><b>Complete Method Set</b> - IDE now recognizes all four methods for @Localized fields</li>
                <li><b>Smart Method Generation</b> - Plugin checks if user has already defined methods to prevent duplicates</li>
                <li><b>Improved Autocomplete</b> - Better developer experience with standard bean methods</li>
                <li><b>Method Overloading</b> - Both default and locale-aware methods coexist seamlessly</li>
            </ul>
            
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
        sourceCompatibility = "17"  // Required by IntelliJ Platform 2024.1+
        targetCompatibility = "17"
    }
    
    // Configure test task for IntelliJ Platform tests
    test {
        useJUnit()
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
