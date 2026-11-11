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
        // JitPack is used ONLY for the adb-kt library. (Its spake2-kt dependency is replaced by
        // the patched copy in third_party/spake2-kt-patched.)
        // The content filter prevents any other artifact from being resolved from JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.rhythmcache") }
        }
    }
}

rootProject.name = "NexusTvRemote"
include(":app")

// Patched SPAKE2 used by adb-kt for Wireless Debugging pairing (see docs/adr-001-adb-library.md).
include(":spake2-kt-patched")
project(":spake2-kt-patched").projectDir = file("third_party/spake2-kt-patched")
