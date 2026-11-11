package com.example.nexusremote.fakes

import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.domain.repository.TvControlClient
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [TvControlClient]. Records every call and lets tests script results.
 * It never opens a socket. State transitions mirror the real client:
 * pair: Pairing -> (previous idle state); connect: Connecting -> Connected | Failed.
 */
class FakeTvControlClient(initial: ConnectionState = ConnectionState.Disconnected) : TvControlClient {

    private val _state = MutableStateFlow(initial)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Every state the client has been in, in order (starting with the initial one). */
    val stateHistory = CopyOnWriteArrayList<ConnectionState>().apply { add(initial) }

    val pairCalls = CopyOnWriteArrayList<Pair<String, Int>>()
    val connectCalls = CopyOnWriteArrayList<TvDevice>()
    val sentCommands = CopyOnWriteArrayList<TvCommand>()
    val sentTexts = CopyOnWriteArrayList<String>()
    val launchedPackages = CopyOnWriteArrayList<String>()
    var disconnectCalls = 0
        private set
    var listCalls = 0
        private set

    var pairResult: NexusResult<Unit> = NexusResult.unit()
    var connectResult: NexusResult<Unit> = NexusResult.unit()

    /** Per-device connect behaviour; defaults to [connectResult]. */
    var connectHandler: (TvDevice) -> NexusResult<Unit> = { connectResult }
    var commandResult: (TvCommand) -> NexusResult<Unit> = { NexusResult.unit() }
    var textResult: NexusResult<Unit> = NexusResult.unit()
    var launchResult: (String) -> NexusResult<Unit> = { NexusResult.unit() }
    var appsResult: NexusResult<List<TvApp>> = NexusResult.Success(emptyList())

    /** Scripts all results at once for a scenario. */
    fun script(scenario: FakeScenario) {
        when (scenario) {
            FakeScenario.SUCCESS -> {
                pairResult = NexusResult.unit()
                connectResult = NexusResult.unit()
                commandResult = { NexusResult.unit() }
                textResult = NexusResult.unit()
            }
            FakeScenario.TIMEOUT -> {
                pairResult = failure(ErrorCode.PAIRING_TIMEOUT)
                connectResult = failure(ErrorCode.CONNECT_TIMEOUT)
            }
            FakeScenario.WRONG_CODE -> pairResult = failure(ErrorCode.PAIRING_CODE_WRONG)
            FakeScenario.AUTH_REVOKED -> {
                connectResult = failure(ErrorCode.AUTH_REVOKED)
                commandResult = { failure(ErrorCode.AUTH_REVOKED) }
            }
        }
    }

    fun setState(state: ConnectionState) {
        _state.value = state
        stateHistory.add(state)
    }

    override suspend fun pair(request: PairingRequest): NexusResult<Unit> {
        pairCalls.add(request.host to request.pairingPort)
        val before = _state.value
        setState(ConnectionState.Pairing)
        request.clear()
        setState(if (before is ConnectionState.Connected) before else ConnectionState.Disconnected)
        return pairResult
    }

    override suspend fun connect(device: TvDevice): NexusResult<Unit> {
        connectCalls.add(device)
        setState(ConnectionState.Connecting)
        val result = connectHandler(device)
        setState(
            when (result) {
                is NexusResult.Success -> ConnectionState.Connected(device.id)
                is NexusResult.Failure -> ConnectionState.Failed(result.errorCode, result.message)
            },
        )
        return result
    }

    override suspend fun sendCommand(command: TvCommand): NexusResult<Unit> {
        sentCommands.add(command)
        if (_state.value !is ConnectionState.Connected) return failure(ErrorCode.COMMAND_FAILED)
        return commandResult(command)
    }

    override suspend fun sendText(text: String): NexusResult<Unit> {
        sentTexts.add(text)
        return textResult
    }

    override suspend fun launchPackage(packageName: String): NexusResult<Unit> {
        launchedPackages.add(packageName)
        return launchResult(packageName)
    }

    override suspend fun listInstalledPackages(): NexusResult<List<TvApp>> {
        listCalls++
        return appsResult
    }

    override suspend fun disconnect() {
        disconnectCalls++
        setState(ConnectionState.Disconnected)
    }

    private fun failure(code: ErrorCode) = NexusResult.Failure(code, "Fake ${code.name}")
}
