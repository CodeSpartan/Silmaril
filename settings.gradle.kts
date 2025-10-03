rootProject.name = "Silmaril"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}



dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // for jewel
        maven("https://packages.jetbrains.team/maven/p/kpm/public/")
        // for jewel icons
        maven("https://www.jetbrains.com/intellij-repository/releases")
        // compose
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}