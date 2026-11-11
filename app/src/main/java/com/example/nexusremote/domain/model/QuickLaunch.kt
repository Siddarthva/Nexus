package com.example.nexusremote.domain.model

/** An app that can be opened from the Remote screen's "Quick launch" row. */
data class QuickLaunchApp(
    /** Stable id stored in DataStore (never the package name). */
    val id: String,
    /** Brand name shown on the button. */
    val label: String,
    val packageName: String,
)

/** The one fixed list of quick-launch apps. Nothing else can appear in the row. */
object QuickLaunchApps {
    // UNVERIFIED-ON-HARDWARE: these are the usual Android TV / Google TV package names; the
    // Haier TV may ship different builds (e.g. a missing app or a regional package).
    val ALL: List<QuickLaunchApp> = listOf(
        QuickLaunchApp(id = "youtube", label = "YouTube", packageName = "com.google.android.youtube.tv"),
        QuickLaunchApp(id = "netflix", label = "Netflix", packageName = "com.netflix.ninja"),
        QuickLaunchApp(id = "prime_video", label = "Prime Video", packageName = "com.amazon.amazonvideo.livingroom"),
    )

    val ids: List<String> get() = ALL.map { it.id }

    fun byId(id: String): QuickLaunchApp? = ALL.firstOrNull { it.id == id }
}

/** The user's order and visibility choices for the quick-launch row (stored in DataStore). */
data class QuickLaunchConfig(
    val order: List<String> = QuickLaunchApps.ids,
    val hidden: Set<String> = emptySet(),
) {
    /** All known apps in the user's order. Unknown ids are ignored; missing ones go last. */
    val ordered: List<QuickLaunchApp>
        get() {
            val known = order.distinct().mapNotNull(QuickLaunchApps::byId)
            return known + QuickLaunchApps.ALL.filterNot { it in known }
        }

    /** The apps shown on the Remote screen. */
    val visible: List<QuickLaunchApp> get() = ordered.filterNot { it.id in hidden }

    /** Returns a new order with [id] moved by [delta] positions (clamped). */
    fun move(id: String, delta: Int): List<String> {
        val ids = ordered.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        if (from < 0) return ids
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        ids.add(to, ids.removeAt(from))
        return ids
    }
}
