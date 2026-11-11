package com.example.nexusremote.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.nexusremote.domain.model.QuickLaunchApps
import com.example.nexusremote.domain.model.QuickLaunchConfig
import com.example.nexusremote.domain.repository.QuickLaunchPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persists the quick-launch order and visibility. Only ids from the fixed
 * [QuickLaunchApps] list are ever stored or read back; anything else is ignored.
 */
class DataStoreQuickLaunchPreferences(private val dataStore: DataStore<Preferences>) : QuickLaunchPreferences {

    override val config: Flow<QuickLaunchConfig> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> decode(p) }
        .distinctUntilChanged()

    override suspend fun setVisible(id: String, visible: Boolean) {
        if (QuickLaunchApps.byId(id) == null) return
        dataStore.edit { p ->
            val hidden = decode(p).hidden
            p[PrefKeys.QUICK_LAUNCH_HIDDEN] = if (visible) hidden - id else hidden + id
        }
    }

    override suspend fun move(id: String, delta: Int) {
        if (QuickLaunchApps.byId(id) == null) return
        dataStore.edit { p ->
            p[PrefKeys.QUICK_LAUNCH_ORDER] = decode(p).move(id, delta).joinToString(",")
        }
    }

    private fun decode(p: Preferences): QuickLaunchConfig {
        val known = QuickLaunchApps.ids.toSet()
        val order = p[PrefKeys.QUICK_LAUNCH_ORDER]
            ?.split(',')
            ?.filter { it in known }
            ?.takeIf { it.isNotEmpty() }
            ?: QuickLaunchApps.ids
        val hidden = p[PrefKeys.QUICK_LAUNCH_HIDDEN].orEmpty().filter { it in known }.toSet()
        return QuickLaunchConfig(order = order, hidden = hidden)
    }
}
