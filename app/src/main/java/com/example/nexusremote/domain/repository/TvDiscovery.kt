package com.example.nexusremote.domain.repository

import com.example.nexusremote.domain.model.DiscoveredTv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Continuous local-network discovery of TVs. Scanning runs only while at least one screen has
 * called [acquire] AND the app is in the foreground ([setForeground]).
 */
interface TvDiscovery {
    val tvs: StateFlow<List<DiscoveredTv>>
    val scanning: StateFlow<Boolean>

    /** A screen that shows discovery results is visible. Pair with [release]. */
    fun acquire()
    fun release()

    /** Clears the results and restarts the scan (if scanning is allowed). */
    fun refresh()

    /** Called by the app's process lifecycle: scanning stops whenever the app is in the background. */
    fun setForeground(foreground: Boolean)
}

/** Discovery that never finds anything. Used where no network scanning is wanted. */
object DisabledTvDiscovery : TvDiscovery {
    override val tvs: StateFlow<List<DiscoveredTv>> = MutableStateFlow(emptyList())
    override val scanning: StateFlow<Boolean> = MutableStateFlow(false)
    override fun acquire() = Unit
    override fun release() = Unit
    override fun refresh() = Unit
    override fun setForeground(foreground: Boolean) = Unit
}
