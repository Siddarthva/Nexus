package com.example.nexusremote.fakes

import com.example.nexusremote.data.security.HostKeyStore
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.domain.repository.CredentialStore
import com.example.nexusremote.domain.repository.DeviceRepository
import com.example.nexusremote.domain.repository.EndpointDiscovery
import com.example.nexusremote.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.concurrent.ConcurrentHashMap

/** In-memory [DeviceRepository] with the same "fall back to the first device" rule as the real one. */
class FakeDeviceRepository(initial: List<TvDevice> = emptyList(), activeId: String? = null) : DeviceRepository {
    val deviceState = MutableStateFlow(initial)
    val activeIdState = MutableStateFlow(activeId)

    override val devices: Flow<List<TvDevice>> = deviceState
    override val activeDeviceId: Flow<String?> = activeIdState
    override val activeDevice: Flow<TvDevice?> = combine(deviceState, activeIdState) { all, id ->
        all.firstOrNull { it.id == id } ?: all.firstOrNull()
    }

    override suspend fun getDevice(id: String): TvDevice? = deviceState.value.firstOrNull { it.id == id }

    override suspend fun upsert(device: TvDevice) {
        val stored = device.copy(pairingPort = null)
        deviceState.update { list ->
            if (list.any { it.id == stored.id }) list.map { if (it.id == stored.id) stored else it } else list + stored
        }
    }

    override suspend fun delete(id: String) {
        deviceState.update { list -> list.filterNot { it.id == id } }
        if (activeIdState.value == id) activeIdState.value = null
    }

    override suspend fun setActiveDevice(id: String?) {
        activeIdState.value = id
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val settingsState = MutableStateFlow(initial)
    val favoritesState = MutableStateFlow<Set<String>>(emptySet())

    override val settings: StateFlow<AppSettings> = settingsState
    override val favoritePackages: StateFlow<Set<String>> = favoritesState

    override suspend fun setHapticsEnabled(enabled: Boolean) = settingsState.update { it.copy(hapticsEnabled = enabled) }
    override suspend fun setSoundEnabled(enabled: Boolean) = settingsState.update { it.copy(soundEnabled = enabled) }
    override suspend fun setConfirmPowerOff(enabled: Boolean) = settingsState.update { it.copy(confirmPowerOff = enabled) }
    override suspend fun toggleFavorite(packageName: String) =
        favoritesState.update { if (packageName in it) it - packageName else it + packageName }
}

/** Fake secure store. Implements both the domain view and the ADB host-key view. */
class FakeCredentialStore : CredentialStore, HostKeyStore {
    private val keys = ConcurrentHashMap<String, KeyPair>()
    val deletedIds = mutableListOf<String>()

    override suspend fun hasCredentials(deviceId: String): Boolean = keys.containsKey(deviceId)

    override suspend fun deleteCredentials(deviceId: String) = deleteKeyPair(deviceId)

    override suspend fun getOrCreateKeyPair(deviceId: String): KeyPair = keys.getOrPut(deviceId) { sharedKeyPair }

    override suspend fun loadKeyPair(deviceId: String): KeyPair? = keys[deviceId]

    override suspend fun deleteKeyPair(deviceId: String) {
        keys.remove(deviceId)
        deletedIds.add(deviceId)
    }

    fun putKey(deviceId: String) {
        keys[deviceId] = sharedKeyPair
    }

    private companion object {
        /** Generated once: the fake transport never uses the key, it only needs a non-null KeyPair. */
        val sharedKeyPair: KeyPair by lazy {
            KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        }
    }
}

class FakeEndpointDiscovery(var port: Int? = null) : EndpointDiscovery {
    var calls = 0
        private set

    override suspend fun findConnectPort(host: String): Int? {
        calls++
        return port
    }
}
