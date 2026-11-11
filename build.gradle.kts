// Top-level build file. Plugins are declared here and applied in :app.
plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere: AGP 9's built-in Kotlin uses the KGP found on the classpath,
    // and this pins it to the catalog version (needed for adb-kt's Kotlin 2.4 metadata).
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
