pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MoodTune"
include(":app")
include(":core:ui")
include(":core:network")
include(":core:utils")
include(":core:database")
include(":core:analytics")
include(":domain")
include(":data")
include(":infrastructure")
include(":config")
include(":feature:home")
include(":feature:playlist")
include(":feature:songdetail")
include(":wear")
