package com.example.nexusremote.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.repository.SettingsRepository
import com.example.nexusremote.util.InputValidators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    private val defaults = AppSettings()

    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val settings: Flow<AppSettings> = prefs.map { p ->
        AppSettings(
            hapticsEnabled = p[PrefKeys.HAPTICS] ?: defaults.hapticsEnabled,
            soundEnabled = p[PrefKeys.SOUND] ?: defaults.soundEnabled,
            confirmPowerOff = p[PrefKeys.CONFIRM_POWER] ?: defaults.confirmPowerOff,
        )
    }.distinctUntilChanged()

    override val favoritePackages: Flow<Set<String>> =
        prefs.map { it[PrefKeys.FAVORITES].orEmpty() }.distinctUntilChanged()

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.HAPTICS] = enabled }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.SOUND] = enabled }
    }

    override suspend fun setConfirmPowerOff(enabled: Boolean) {
        dataStore.edit { it[PrefKeys.CONFIRM_POWER] = enabled }
    }

    override suspend fun toggleFavorite(packageName: String) {
        if (!InputValidators.isValidPackageName(packageName)) return
        dataStore.edit { p ->
            val current = p[PrefKeys.FAVORITES].orEmpty()
            p[PrefKeys.FAVORITES] = if (packageName in current) current - packageName else current + packageName
        }
    }
}
