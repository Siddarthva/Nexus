package com.example.nexusremote.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreDeviceRepository(private val dataStore: DataStore<Preferences>) : DeviceRepository {

    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val devices: Flow<List<TvDevice>> =
        prefs.map { DeviceProfileCodec.decode(it[PrefKeys.DEVICES_JSON]) }.distinctUntilChanged()

    override val activeDeviceId: Flow<String?> =
        prefs.map { it[PrefKeys.ACTIVE_DEVICE_ID] }.distinctUntilChanged()

    override val activeDevice: Flow<TvDevice?> =
        prefs.map { p ->
            val all = DeviceProfileCodec.decode(p[PrefKeys.DEVICES_JSON])
            val activeId = p[PrefKeys.ACTIVE_DEVICE_ID]
            all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
        }.distinctUntilChanged()

    override suspend fun getDevice(id: String): TvDevice? = devices.first().firstOrNull { it.id == id }

    override suspend fun upsert(device: TvDevice) {
        val toStore = device.copy(pairingPort = null)
        dataStore.edit { p ->
            val current = DeviceProfileCodec.decode(p[PrefKeys.DEVICES_JSON])
            val updated = if (current.any { it.id == toStore.id }) {
                current.map { if (it.id == toStore.id) toStore else it }
            } else {
                current + toStore
            }
            p[PrefKeys.DEVICES_JSON] = DeviceProfileCodec.encode(updated)
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { p ->
            val remaining = DeviceProfileCodec.decode(p[PrefKeys.DEVICES_JSON]).filterNot { it.id == id }
            p[PrefKeys.DEVICES_JSON] = DeviceProfileCodec.encode(remaining)
            if (p[PrefKeys.ACTIVE_DEVICE_ID] == id) p.remove(PrefKeys.ACTIVE_DEVICE_ID)
        }
    }

    override suspend fun setActiveDevice(id: String?) {
        dataStore.edit { p ->
            if (id == null) p.remove(PrefKeys.ACTIVE_DEVICE_ID) else p[PrefKeys.ACTIVE_DEVICE_ID] = id
        }
    }
}
