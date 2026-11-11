package com.example.nexusremote.adb

import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.util.InputValidators

/**
 * Parses package listings from the TV. Every package name is re-validated, so unexpected
 * output can never turn into an unsafe launch target.
 */
internal object PackageListParser {

    /** Parses `cmd package query-activities --brief` output (`pkg/cls` tokens). */
    fun parseActivityListing(output: String): List<TvApp> =
        output.lineSequence()
            .map { it.trim() }
            .filter { '/' in it }
            .map { it.substringBefore('/') }
            .toApps()

    /** Parses `pm list packages` output (`package:<name>` lines). */
    fun parsePmListing(output: String): List<TvApp> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .toApps()

    private fun Sequence<String>.toApps(): List<TvApp> =
        filter { InputValidators.isValidPackageName(it) }
            .distinct()
            .map { TvApp(packageName = it, label = AppLabels.labelFor(it)) }
            .toList()
}

/**
 * The shell listing has no human-readable labels, so well-known TV apps get a fixed label
 * and everything else gets a readable name derived from its package name.
 */
internal object AppLabels {
    private val KNOWN = mapOf(
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube.tvmusic" to "YouTube Music",
        "com.google.android.youtube.tvkids" to "YouTube Kids",
        "com.netflix.ninja" to "Netflix",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "in.startv.hotstar" to "JioHotstar",
        "com.spotify.tv.android" to "Spotify",
        "com.google.android.apps.tv.launcherx" to "Google TV Home",
        "com.google.android.tvlauncher" to "Android TV Home",
        "com.android.vending" to "Play Store",
        "com.android.tv.settings" to "Settings",
        "com.google.android.play.games" to "Play Games",
        "com.plexapp.android" to "Plex",
        "org.videolan.vlc" to "VLC",
        "com.apple.atve.androidtv.appletv" to "Apple TV",
    )

    private val GENERIC_SEGMENTS = setOf("com", "org", "net", "android", "tv", "app", "apps", "google", "in", "io")

    fun labelFor(packageName: String): String {
        KNOWN[packageName]?.let { return it }
        val segments = packageName.split('.')
        val meaningful = segments.lastOrNull { it.lowercase() !in GENERIC_SEGMENTS } ?: segments.last()
        return meaningful.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
