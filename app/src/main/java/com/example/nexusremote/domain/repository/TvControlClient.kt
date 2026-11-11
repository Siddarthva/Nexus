package com.example.nexusremote.domain.repository

import com.example.nexusremote.domain.command.OpenSettingsCommand
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-neutral TV control API. The UI and use cases depend only on this interface;
 * ADB details (sockets, ports, keycodes, shell strings) live behind it.
 *
 * Every function is main-safe: implementations switch to an IO dispatcher internally.
 */
interface TvControlClient {
    val connectionState: StateFlow<ConnectionState>

    /** Pairs with the TV's temporary pairing endpoint. The request's code is wiped afterwards. */
    suspend fun pair(request: PairingRequest): NexusResult<Unit>

    /** Connects to the device's separate connect endpoint using its stored host key. */
    suspend fun connect(device: TvDevice): NexusResult<Unit>

    suspend fun sendCommand(command: TvCommand): NexusResult<Unit>

    /** Sends text to the focused field. Text outside the supported set is rejected. */
    suspend fun sendText(text: String): NexusResult<Unit>

    suspend fun launchPackage(packageName: String): NexusResult<Unit>

    suspend fun listInstalledPackages(): NexusResult<List<TvApp>>

    /**
     * Opens one fixed settings page on the TV (no setting is changed). Clients that cannot do
     * this report UNSUPPORTED.
     */
    suspend fun openSettings(command: OpenSettingsCommand): NexusResult<Unit> =
        NexusResult.Failure(ErrorCode.UNSUPPORTED, "Not supported by this client")

    suspend fun disconnect()
}
