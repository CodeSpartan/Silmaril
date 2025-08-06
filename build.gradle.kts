import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "2.1.21"
    // lets you know how to update packages with this command: ./gradlew dependencyUpdates -Drevision=release
    id("com.github.ben-manes.versions") version "0.52.0"
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}

group = "ru.adan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    // Add the kotlinx-coroutines-swing dependency for enabling Dispatchers.Main on Compose Desktop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // jackson xml
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
    // this allows us to load fonts from the composeResources folder and load them in a new way
    implementation(compose.components.resources)
    // For Kotlin Scripting
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:2.1.21")
    // JSR
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.1.21")
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
    implementation("io.insert-koin:koin-ktor")
    // SLF4J Logger
    implementation("io.insert-koin:koin-logger-slf4j")
}

compose.desktop {
    application {
        mainClass = "ru.adan.silmaril.MainKt"

        nativeDistributions {
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
    }
}
