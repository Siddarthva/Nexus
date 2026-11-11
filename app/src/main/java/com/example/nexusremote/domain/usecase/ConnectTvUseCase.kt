package com.example.nexusremote.domain.usecase

import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.domain.repository.DeviceRepository
import com.example.nexusremote.domain.repository.EndpointDiscovery
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.NexusResult

/**
 * Connects to a saved TV. If the saved connect port no longer answers (typical after a TV
 * reboot), it asks [discovery] for the currently advertised port and retries once with it.
 * On success the profile's last-connected time (and port, if it changed) is saved and the
 * TV becomes the active one.
 */
class ConnectTvUseCase(
    private val client: TvControlClient,
    private val devices: DeviceRepository,
    private val discovery: EndpointDiscovery,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(device: TvDevice): NexusResult<TvDevice> {
        var target = device
        var result = client.connect(target)

        if (result is NexusResult.Failure && result.errorCode in PORT_MAY_HAVE_CHANGED) {
            val discoveredPort = discovery.findConnectPort(device.host)
            if (discoveredPort != null && discoveredPort != device.connectPort) {
                target = device.copy(connectPort = discoveredPort)
                result = client.connect(target)
            }
        }

        return when (result) {
            is NexusResult.Failure -> result
            is NexusResult.Success -> {
                val updated = target.copy(lastConnectedAt = clock(), pairingPort = null)
                devices.upsert(updated)
                devices.setActiveDevice(updated.id)
                NexusResult.Success(updated)
            }
        }
    }

    private companion object {
        val PORT_MAY_HAVE_CHANGED = setOf(
            ErrorCode.WIRELESS_DEBUG_OFF,
            ErrorCode.CONNECT_TIMEOUT,
            ErrorCode.NETWORK_UNREACHABLE,
        )
    }
}
