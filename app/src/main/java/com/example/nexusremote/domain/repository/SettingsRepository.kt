package com.example.nexusremote.domain.repository

import com.example.nexusremote.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val favoritePackages: Flow<Set<String>>

    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setConfirmPowerOff(enabled: Boolean)
    suspend fun toggleFavorite(packageName: String)
}
