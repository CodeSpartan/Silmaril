import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinSerialization)
    id("com.google.devtools.ksp") version "2.2.20-2.0.2"
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("com.charleskorn.kaml:kaml:0.85.0")

                // Koin DI
                implementation(project.dependencies.platform("io.insert-koin:koin-bom:4.0.3"))
                implementation("io.insert-koin:koin-core")
                implementation("io.insert-koin:koin-compose")
                implementation("io.insert-koin:koin-compose-viewmodel")

                // ktor for networking
                implementation("io.ktor:ktor-network:3.2.3")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Shared JVM source set for code that works on both Android and Desktop
        val jvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Jackson for XML (shared between Android and Desktop)
                implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")

                // EvalEx for math and logical expression evaluation
                implementation("com.ezylang:EvalEx:3.5.0")
            }
        }

        val androidMain by getting {
            dependsOn(jvmMain)
            dependencies {
                // Android-specific Compose
                implementation(libs.androidx.activity.compose)

                // Koin Android
                implementation("io.insert-koin:koin-android")
                implementation("io.insert-koin:koin-androidx-compose")

                // Coroutines Android
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

                // StAX API and implementation for Android (Android doesn't include javax.xml.stream)
                implementation("javax.xml.stream:stax-api:1.0-2")
                implementation("com.fasterxml.woodstox:woodstox-core:6.5.1")
            }
        }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependencies {
                // Desktop Compose
                implementation(compose.desktop.currentOs) {
                    exclude(group = "org.jetbrains.compose.material")
                }

                // Coroutines Swing for Desktop
                implementation(libs.kotlinx.coroutinesSwing)

                // Jewel UI
                implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.30.0-252.26252")
                implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.30.0-252.26252")
                implementation("com.jetbrains.intellij.platform:icons:252.26199.158")

                // Kotlin Scripting (JVM only)
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:${libs.versions.kotlin.get()}")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:${libs.versions.kotlin.get()}")
                implementation("org.jetbrains.kotlin:kotlin-scripting-common:${libs.versions.kotlin.get()}")
                implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:${libs.versions.kotlin.get()}")

                // Logging (JVM)
                implementation("io.insert-koin:koin-logger-slf4j")
                implementation("ch.qos.logback:logback-classic:1.5.16")
                implementation("io.github.oshai:kotlin-logging-jvm:7.0.12")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.1")

                // zlib compression
                implementation("com.jcraft:jzlib:1.1.3")

                // Koin navigation
                implementation("io.insert-koin:koin-compose-viewmodel-navigation")
            }
        }

        val desktopTest by getting
    }
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

android {
    namespace = "ru.adan.silmaril"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.adan.silmaril"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Disable lint checks for release builds to avoid CI failures
        // Lint has compatibility issues with newer Kotlin versions
        checkReleaseBuilds = false
        abortOnError = false
    }
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
            "-Dskiko.renderApi=OPENGL"
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