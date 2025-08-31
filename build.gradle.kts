import org.jetbrains.compose.desktop.application.dsl.TargetFormat

kotlin {
    jvmToolchain(21)
}

plugins {
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // alias(libs.plugins.composeHotReload) // don't need it, since I don't use hotreload. maybe later?
    alias(libs.plugins.kotlinSerialization) // xml, yaml, json
    // lets you know how to update packages with this command: ./gradlew dependencyUpdates -Drevision=release
    id("com.github.ben-manes.versions") version "0.52.0"
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}

// This will version all classes with the version set in this file. Allows us to know the program version in the code.
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

group = "ru.adan"
// Use -Pversion=... if provided
version = providers.gradleProperty("version").orElse("1.0-SNAPSHOT")

dependencies {
    // Add the kotlinx-coroutines-swing dependency for enabling Dispatchers.Main on Compose Desktop
    implementation(libs.kotlinx.coroutinesSwing)
    // json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // jackson xml
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
    // this allows us to load fonts from the composeResources folder and load them in a new way
    implementation(compose.components.resources)
    // For Kotlin Scripting
    /**
     * The Kotlin team has announced changes/deprecations around scripting for K2,
     * including plans to drop JSR-223 and some related artifacts after Kotlin 2.3.
     * Do we even need them??
     */
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:${libs.versions.kotlin.get()}")
    // for icons
    implementation("org.jetbrains.compose.material:material-desktop:1.8.2")
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")
    // Koin
    implementation(project.dependencies.platform("io.insert-koin:koin-bom:4.0.3"))
    implementation("io.insert-koin:koin-core")
    implementation("io.insert-koin:koin-compose")
    implementation("io.insert-koin:koin-compose-viewmodel")
    implementation("io.insert-koin:koin-compose-viewmodel-navigation")
    // The Koin compiler that KSP will use
    ksp("io.insert-koin:koin-ksp-compiler:2.1.0")
    // Koin for Ktor
    // not needed?
    // implementation("io.insert-koin:koin-ktor")
    // SLF4J Logger for Koin
    implementation("io.insert-koin:koin-logger-slf4j")
    // SLF4J Backend (Logback)
    implementation("ch.qos.logback:logback-classic:1.5.16")
    // Kotlin Logging, the facade for idiomatic logging in Kotlin
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.12")
    // Something that solves the MDC/Coroutine issue for SLF4J (in this case, Logback)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.1")
    // ktor.io
    implementation("io.ktor:ktor-network:3.2.3")
    // zlib
    implementation("com.jcraft:jzlib:1.1.3")
    // Kaml (kotlin yaml)
    implementation("com.charleskorn.kaml:kaml:0.85.0")
    // helps XML with serialization somehow
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")

    /** jewel imports */
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    // See https://github.com/JetBrains/Jewel/releases for the release notes
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.29.0-252.24604")
    // For custom decorated windows
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.29.0-252.24604")
    // Jewel Icons
    implementation("com.jetbrains.intellij.platform:icons:252.23892.530")
    /** end of jewel imports */

    // already brought in by currentOs
    //implementation(compose.runtime)
    //implementation(compose.foundation)
    //implementation(compose.material3)
    //implementation(compose.ui)
    implementation(compose.components.uiToolingPreview) // @TODO: previews don't currently work
    // I don't use only if you use ViewModel/lifecycle APIs in Desktop, we use Koin koin-compose-viewmodel
    //implementation(libs.androidx.lifecycle.viewmodelCompose)
    //implementation(libs.androidx.lifecycle.runtimeCompose)
}

/**
 * Reminder to self: to test a release build in IDE, runDistributable
 * To debug an issue in release, uncomment console = true
  */
compose.desktop {
    application {
        mainClass = "ru.adan.silmaril.MainKt"
        // uncomment and set path to JBR, in case your system's default is different
        //javaHome = "C:/Users/<UserName>/.jdks/jbr-21.0.8"

        jvmArgs += listOf(
//            "-XX:NativeMemoryTracking=detail",
//            "-Xlog:gc*:stdout:time,level,tags",
//            "-Xlog:class+unload=info",
        )

        nativeDistributions {

            modules(
                "java.naming", // necessary for logback
                "jdk.unsupported", // for DSL scripts
//                "jdk.attach",
//                "jdk.management",
            )

//            windows { console = true } // adds --win-console for jpackage

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            packageName = "Silmaril"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
            linux {
                iconFile.set(project.file("icons/icon_256.png"))
            }
        }

        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                }
            }
        }
    }
}
