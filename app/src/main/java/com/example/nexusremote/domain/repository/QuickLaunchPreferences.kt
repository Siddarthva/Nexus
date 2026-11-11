package com.example.nexusremote.domain.repository

import com.example.nexusremote.domain.model.QuickLaunchApps
import com.example.nexusremote.domain.model.QuickLaunchConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Order and visibility of the quick-launch apps. Production uses DataStore. */
interface QuickLaunchPreferences {
    val config: Flow<QuickLaunchConfig>
    suspend fun setVisible(id: String, visible: Boolean)
    suspend fun move(id: String, delta: Int)
}

/** Non-persistent implementation, for code paths that do not need storage (e.g. test fixtures). */
class InMemoryQuickLaunchPreferences : QuickLaunchPreferences {
    private val state = MutableStateFlow(QuickLaunchConfig())
    override val config: Flow<QuickLaunchConfig> = state

    override suspend fun setVisible(id: String, visible: Boolean) {
        if (QuickLaunchApps.byId(id) == null) return
        state.update { it.copy(hidden = if (visible) it.hidden - id else it.hidden + id) }
    }

    override suspend fun move(id: String, delta: Int) {
        state.update { it.copy(order = it.move(id, delta)) }
    }
}
