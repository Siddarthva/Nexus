package com.example.nexusremote.ui.screens.pairing

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.R
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.PairingRequest
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.ui.components.UiMessage
import com.example.nexusremote.ui.navigation.Routes
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class TvBrand(val manufacturerHint: String?) { HAIER("Haier"), OTHER(null) }

data class PairingForm(
    val name: String = "",
    val ip: String = "",
    val pairingPort: String = "",
    val connectPort: String = "",
    /** Held only while the user types; cleared the moment pairing starts. */
    val code: String = "",
    val brand: TvBrand = TvBrand.HAIER,
)

data class FieldErrors(
    @StringRes val name: Int? = null,
    @StringRes val ip: Int? = null,
    @StringRes val pairingPort: Int? = null,
    @StringRes val connectPort: Int? = null,
    @StringRes val code: Int? = null,
) {
    val hasAny: Boolean get() = listOf(name, ip, pairingPort, connectPort, code).any { it != null }
}

sealed interface PairingStep {
    data object Idle : PairingStep
    data object Pairing : PairingStep
    data object Connecting : PairingStep
    data class Success(val deviceName: String) : PairingStep
    data class PairedNotConnected(val errorCode: ErrorCode) : PairingStep
    data class Failed(val errorCode: ErrorCode) : PairingStep
}

data class PairingUiState(
    val form: PairingForm = PairingForm(),
    val errors: FieldErrors = FieldErrors(),
    val step: PairingStep = PairingStep.Idle,
) {
    val busy: Boolean get() = step == PairingStep.Pairing || step == PairingStep.Connecting
    val samePortWarning: Boolean
        get() = form.pairingPort.isNotBlank() && form.pairingPort.trim() == form.connectPort.trim()

    /** The Pair button is enabled only when every field is valid and nothing is in progress. */
    val canSubmit: Boolean get() = !busy && !PairingValidation.validate(form, showEmpty = true).hasAny
}

/** Field validation shared by the live (as-you-type) check and the submit check. */
object PairingValidation {
    /**
     * @param showEmpty when false, empty fields are not flagged (so untouched fields don't
     *   show errors while typing); when true, empty fields count as errors.
     */
    fun validate(form: PairingForm, showEmpty: Boolean, requireCode: Boolean = true): FieldErrors {
        fun check(value: String, valid: Boolean, @StringRes error: Int): Int? =
            when {
                valid -> null
                value.isBlank() && !showEmpty -> null
                else -> error
            }
        return FieldErrors(
            name = check(form.name, InputValidators.isValidFriendlyName(form.name), R.string.pair_error_name),
            ip = check(form.ip, InputValidators.isValidIpv4(form.ip), R.string.pair_error_ip),
            pairingPort = if (!requireCode) null else
                check(form.pairingPort, InputValidators.parsePort(form.pairingPort) != null, R.string.pair_error_port),
            connectPort = check(form.connectPort, InputValidators.parsePort(form.connectPort) != null, R.string.pair_error_port),
            code = if (!requireCode) null else
                check(form.code, InputValidators.isValidPairingCode(form.code), R.string.pair_error_code),
        )
    }
}

class PairingViewModel(
    savedStateHandle: SavedStateHandle,
    private val container: AppContainer,
) : ViewModel() {

    private val existingId: String? = savedStateHandle.get<String>(Routes.PAIR_ARG_DEVICE_ID)
        ?.takeIf { InputValidators.isValidDeviceId(it) }

    /** Keys are stored under this id. Re-pairing an existing TV reuses its id (and key). */
    private val deviceId: String = existingId ?: UUID.randomUUID().toString()
    private var existingDevice: TvDevice? = null
    private var pairedDevice: TvDevice? = null

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    fun onLocalNetworkDenied() {
        messageChannel.trySend(UiMessage.Text(R.string.error_local_network_denied))
    }

    init {
        if (existingId != null) {
            viewModelScope.launch {
                container.deviceRepository.getDevice(existingId)?.let { device ->
                    existingDevice = device
                    _uiState.update {
                        it.copy(
                            form = it.form.copy(
                                name = device.friendlyName,
                                ip = device.host,
                                connectPort = device.connectPort.toString(),
                                brand = if (device.manufacturerHint == TvBrand.HAIER.manufacturerHint) TvBrand.HAIER else TvBrand.OTHER,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun setDefaultName(defaultName: String) {
        _uiState.update { if (it.form.name.isEmpty()) it.copy(form = it.form.copy(name = defaultName)) else it }
    }

    fun onNameChange(value: String) = updateForm { copy(name = value.take(InputValidators.MAX_FRIENDLY_NAME_LENGTH)) }
    fun onIpChange(value: String) = updateForm { copy(ip = value.filter { it.isDigit() || it == '.' }.take(15)) }
    fun onPairingPortChange(value: String) = updateForm { copy(pairingPort = value.filter(Char::isDigit).take(5)) }
    fun onConnectPortChange(value: String) = updateForm { copy(connectPort = value.filter(Char::isDigit).take(5)) }
    fun onCodeChange(value: String) = updateForm { copy(code = value.filter(Char::isDigit).take(6)) }
    fun onBrandChange(brand: TvBrand) = updateForm { copy(brand = brand) }

    private fun updateForm(change: PairingForm.() -> PairingForm) {
        _uiState.update {
            val form = it.form.change()
            it.copy(form = form, errors = PairingValidation.validate(form, showEmpty = false))
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.busy) return
        val errors = PairingValidation.validate(state.form, showEmpty = true)
        if (errors.hasAny) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        val form = state.form
        val request = PairingRequest(
            deviceId = deviceId,
            host = form.ip.trim(),
            pairingPort = InputValidators.parsePort(form.pairingPort) ?: return,
            code = form.code.toCharArray(),
        )
        // The code leaves UI state immediately; PairingRequest wipes its copy after use.
        _uiState.update { it.copy(form = it.form.copy(code = ""), step = PairingStep.Pairing) }

        viewModelScope.launch {
            when (val paired = container.pairTv(request)) {
                is NexusResult.Failure -> {
                    _uiState.update { it.copy(step = PairingStep.Failed(paired.errorCode)) }
                    return@launch
                }
                is NexusResult.Success -> Unit
            }
            val device = TvDevice(
                id = deviceId,
                friendlyName = form.name.trim(),
                host = form.ip.trim(),
                connectPort = InputValidators.parsePort(form.connectPort) ?: return@launch,
                lastConnectedAt = existingDevice?.lastConnectedAt,
                manufacturerHint = form.brand.manufacturerHint,
            )
            when (val saved = container.saveDevice(device, makeActive = true)) {
                is NexusResult.Failure -> {
                    _uiState.update { it.copy(step = PairingStep.Failed(saved.errorCode)) }
                    return@launch
                }
                is NexusResult.Success -> pairedDevice = saved.value
            }
            connect(device)
        }
    }

    /** After a successful pairing, retry the connection (e.g. with a corrected connect port). */
    fun retryConnect() {
        val base = pairedDevice ?: return
        val errors = PairingValidation.validate(_uiState.value.form, showEmpty = true, requireCode = false)
        if (errors.connectPort != null) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        val port = InputValidators.parsePort(_uiState.value.form.connectPort) ?: return
        val device = base.copy(connectPort = port)
        viewModelScope.launch {
            container.saveDevice(device)
            pairedDevice = device
            connect(device)
        }
    }

    private suspend fun connect(device: TvDevice) {
        _uiState.update { it.copy(step = PairingStep.Connecting) }
        val step = when (val result = container.connectTv(device)) {
            is NexusResult.Success -> PairingStep.Success(result.value.friendlyName)
            is NexusResult.Failure -> PairingStep.PairedNotConnected(result.errorCode)
        }
        _uiState.update { it.copy(step = step) }
    }

    override fun onCleared() {
        // A key generated for a brand-new TV that never paired is useless: delete it.
        if (pairedDevice == null && existingId == null) {
            val id = deviceId
            container.appScope.launch { container.credentialStore.deleteCredentials(id) }
        }
        super.onCleared()
    }
}
