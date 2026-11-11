package com.example.nexusremote.ui.screens.paircode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.model.DiscoveredTv
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.ui.navigation.Routes
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface PairCodeStep {
    data object Idle : PairCodeStep
    data object Pairing : PairCodeStep
    data object Connecting : PairCodeStep
    /** Paired, but the TV's connect service has not been seen yet; the user taps Connect. */
    data object WaitingForConnectPort : PairCodeStep
    data class Failed(val errorCode: ErrorCode) : PairCodeStep
}

data class PairCodeUiState(
    val tv: DiscoveredTv? = null,
    val savedName: String? = null,
    val code: String = "",
    val step: PairCodeStep = PairCodeStep.Idle,
    /** True once pairing succeeded; the button then connects instead of pairing again. */
    val paired: Boolean = false,
) {
    val busy: Boolean get() = step == PairCodeStep.Pairing || step == PairCodeStep.Connecting
    val waitingForPairingScreen: Boolean get() = !paired && tv != null && tv.pairingPort == null
    val canPair: Boolean
        get() = !paired && !busy && tv?.pairingPort != null && InputValidators.isValidPairingCode(code)
    val canConnect: Boolean get() = paired && !busy && tv?.connectPort != null
}

/**
 * Pairs with a TV found by discovery. The only input is the six-digit code; the pairing and
 * connect ports come from discovery. Pairing runs once per tap and is never retried
 * automatically. The code is cleared as soon as pairing starts.
 */
class PairCodeViewModel(
    savedStateHandle: SavedStateHandle,
    private val container: AppContainer,
) : ViewModel() {

    private val host: String = savedStateHandle.get<String>(Routes.PAIR_CODE_ARG_HOST)
        ?.takeIf(InputValidators::isValidIpv4).orEmpty()

    /** Re-pairing a saved TV keeps its id (and stored key); a new TV gets a fresh id. */
    private val existingId: String? = savedStateHandle.get<String>(Routes.PAIR_ARG_DEVICE_ID)
        ?.takeIf(InputValidators::isValidDeviceId)
    private val deviceId: String = existingId ?: UUID.randomUUID().toString()

    private val code = MutableStateFlow("")
    private val step = MutableStateFlow<PairCodeStep>(PairCodeStep.Idle)
    private val paired = MutableStateFlow(false)
    private val savedName = MutableStateFlow<String?>(null)
    private var saved: TvDevice? = null
    private var pendingName: String? = null

    private val eventChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits when the TV is paired and connected: the screen opens the remote. */
    val connected: Flow<Unit> = eventChannel.receiveAsFlow()

    private val tv = container.tvDiscovery.tvs.map { list -> list.firstOrNull { it.host == host } }

    val uiState: StateFlow<PairCodeUiState> = combine(tv, code, step, paired, savedName) { t, c, s, p, n ->
        PairCodeUiState(tv = t, savedName = n, code = c, step = s, paired = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PairCodeUiState())

    init {
        if (existingId != null) {
            viewModelScope.launch {
                saved = container.deviceRepository.getDevice(existingId)
                savedName.value = saved?.friendlyName
            }
        }
    }

    /** Keeps discovery running while this screen is visible, so the pairing port can appear. */
    fun startScanning() = container.tvDiscovery.acquire()
    fun stopScanning() = container.tvDiscovery.release()

    fun onCodeChange(value: String) {
        code.value = value.filter { it in '0'..'9' }.take(6)
    }

    /** User tapped Pair. [displayName] is the name to save for a new TV. */
    fun pair(displayName: String) {
        val state = uiState.value
        if (!state.canPair) return
        val pairingPort = state.tv?.pairingPort ?: return
        val request = PairingRequest(deviceId, host, pairingPort, code.value.toCharArray())
        code.value = ""
        step.value = PairCodeStep.Pairing
        pendingName = displayName
        viewModelScope.launch {
            when (val result = container.pairTv(request)) {
                is NexusResult.Failure -> step.value = PairCodeStep.Failed(result.errorCode)
                is NexusResult.Success -> {
                    paired.value = true
                    val connectPort = uiState.value.tv?.connectPort
                    if (connectPort == null) step.value = PairCodeStep.WaitingForConnectPort else saveAndConnect(connectPort)
                }
            }
        }
    }

    /** User tapped Connect after pairing (port appeared later, or a connect attempt failed). */
    fun connect() {
        val state = uiState.value
        if (!state.canConnect) return
        viewModelScope.launch { saveAndConnect(state.tv!!.connectPort!!) }
    }

    private suspend fun saveAndConnect(connectPort: Int) {
        step.value = PairCodeStep.Connecting
        val discovered = uiState.value.tv
        val device = TvDevice(
            id = deviceId,
            friendlyName = (saved?.friendlyName ?: pendingName?.takeIf { it.isNotBlank() } ?: host)
                .take(InputValidators.MAX_FRIENDLY_NAME_LENGTH),
            host = host,
            connectPort = connectPort,
            lastConnectedAt = saved?.lastConnectedAt,
            manufacturerHint = saved?.manufacturerHint,
            mdnsId = discovered?.mdnsId ?: saved?.mdnsId,
        )
        when (val save = container.saveDevice(device, makeActive = true)) {
            is NexusResult.Failure -> {
                step.value = PairCodeStep.Failed(save.errorCode)
                return
            }
            is NexusResult.Success -> saved = save.value
        }
        when (val result = container.connectTv(device)) {
            is NexusResult.Success -> {
                step.value = PairCodeStep.Idle
                eventChannel.send(Unit)
            }
            is NexusResult.Failure -> step.value = PairCodeStep.Failed(result.errorCode)
        }
    }

    override fun onCleared() {
        // A key generated for a new TV that never finished pairing is useless: delete it.
        if (!paired.value && existingId == null) {
            val id = deviceId
            container.appScope.launch { container.credentialStore.deleteCredentials(id) }
        }
        super.onCleared()
    }
}
