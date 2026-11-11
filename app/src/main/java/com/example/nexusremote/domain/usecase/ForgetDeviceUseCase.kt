package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.repository.CredentialStore
import com.example.nexusremote.domain.repository.DeviceRepository
import com.example.nexusremote.domain.repository.TvControlClient
import kotlinx.coroutines.flow.first

/**
 * Removes a TV completely: disconnects if it is the connected TV, deletes its ADB host key
 * from the secure store, deletes its profile and makes another saved TV active (if any).
 *
 * This cannot revoke the authorization on the TV itself; the README tells users to also use
 * "Revoke debugging authorizations" on the TV.
 */
class ForgetDeviceUseCase(
    private val client: TvControlClient,
    private val devices: DeviceRepository,
    private val credentials: CredentialStore,
) {
    suspend operator fun invoke(deviceId: String) {
        val state = client.connectionState.value
        if (state is ConnectionState.Connected && state.deviceId == deviceId) {
            client.disconnect()
        }
        credentials.deleteCredentials(deviceId)
        val wasActive = devices.activeDeviceId.first() == deviceId
        devices.delete(deviceId)
        if (wasActive) {
            devices.setActiveDevice(devices.devices.first().firstOrNull()?.id)
        }
    }
}
