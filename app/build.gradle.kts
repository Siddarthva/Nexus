import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.nexusremote"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.nexusremote"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Optional, local-only values for the @Ignore'd real-pairing smoke test. They come from
        // the git-ignored test-config.properties and are passed as instrumentation arguments.
        // Nothing is hardcoded. See docs/test-plan.md.
        val testConfigFile = rootProject.file("test-config.properties")
        if (testConfigFile.isFile) {
            val testConfig = Properties().apply { testConfigFile.inputStream().use { load(it) } }
            testConfig.stringPropertyNames().filter { it.startsWith("smoke") }.forEach { key ->
                testInstrumentationRunnerArguments["nexus.$key"] = testConfig.getProperty(key)
            }
        }
    }

    testOptions {
        // android.util.Log (used by SafeLog) returns defaults in JVM unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        // Fakes shared by JVM unit tests (src/test) and instrumented tests (src/androidTest).
        // AGP 9 built-in Kotlin compiles .kt files from the `kotlin` directory set.
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // No signing config: release builds are unsigned. No keystore is required.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            // BouncyCastle (pulled in by adb-kt) ships duplicate metadata files.
            excludes += listOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.adb.kt) {
        // spake2-kt v1.0.2 reads BigInteger.TWO (Android 13+ only) and crashes pairing on
        // Android 8-12. It is replaced by the patched copy below. See docs/adr-001-adb-library.md.
        exclude(group = "com.github.rhythmcache", module = "spake2-kt")
    }
    implementation(project(":spake2-kt-patched"))
    debugImplementation(libs.androidx.compose.ui.tooling)

    // JVM unit tests (src/test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)

    // Instrumented tests on the phone emulator (src/androidTest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    // Declares the empty ComponentActivity that createComposeRule() hosts content in.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
