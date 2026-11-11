// Vendored, patched copy of rhythmcache/spake2-kt v1.0.2 (Apache-2.0, see LICENSE).
// It replaces the JitPack artifact com.github.rhythmcache:spake2-kt that adb-kt depends on.
// The only code change is in internal/field/ScalarOps.kt (BigInteger.TWO -> BigInteger.valueOf(2)),
// so pairing works on Android 8-12. See docs/adr-001-adb-library.md.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Same module name as upstream, so Kotlin `internal` name mangling matches the original jar.
        moduleName.set("spake2-kt")
    }
}
