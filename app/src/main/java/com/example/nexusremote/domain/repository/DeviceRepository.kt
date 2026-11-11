package com.example.nexusremote.domain.repository

import com.example.nexusremote.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow

/** Persists non-secret TV profiles and which TV is active. */
interface DeviceRepository {
    val devices: Flow<List<TvDevice>>
    val activeDeviceId: Flow<String?>

    /** The active device, or null if none is saved/selected. */
    val activeDevice: Flow<TvDevice?>

    suspend fun getDevice(id: String): TvDevice?

    /** Inserts or replaces by id. The pairing port is never persisted. */
    suspend fun upsert(device: TvDevice)

    suspend fun delete(id: String)

    suspend fun setActiveDevice(id: String?)
}
