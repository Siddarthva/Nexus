package com.example.nexusremote.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.R
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.QuickLaunchConfig
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.ui.components.UiMessage
import com.example.nexusremote.util.InputValidators
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val devices: List<TvDevice> = emptyList(),
    val activeDeviceId: String? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val settings: AppSettings = AppSettings(),
    val busyDeviceId: String? = null,
    /** Quick-launch apps in the user's order, plus which are hidden. */
    val quickLaunch: QuickLaunchConfig = QuickLaunchConfig(),
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val busyDeviceId = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        container.deviceRepository.devices,
        container.deviceRepository.activeDeviceId,
        container.tvClient.connectionState,
        container.settingsRepository.settings,
        combine(busyDeviceId, container.quickLaunchPreferences.config) { busy, quick -> busy to quick },
    ) { devices, activeId, connection, settings, (busy, quick) ->
        SettingsUiState(devices, activeId ?: devices.firstOrNull()?.id, connection, settings, busy, quick)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Shows or hides a quick-launch app on the Remote screen (stored in DataStore). */
    fun setQuickLaunchVisible(id: String, visible: Boolean) =
        viewModelScope.launch { container.quickLaunchPreferences.setVisible(id, visible) }

    /** Moves a quick-launch app up (-1) or down (+1) (stored in DataStore). */
    fun moveQuickLaunch(id: String, delta: Int) =
        viewModelScope.launch { container.quickLaunchPreferences.move(id, delta) }

    /** Saves a new name and/or connect port (the port changes after a TV reboot). */
    fun saveEdit(device: TvDevice, name: String, connectPortText: String): Boolean {
        val port = InputValidators.parsePort(connectPortText)
        if (!InputValidators.isValidFriendlyName(name) || port == null) {
            messageChannel.trySend(UiMessage.Error(ErrorCode.INVALID_INPUT))
            return false
        }
        viewModelScope.launch {
            when (val result = container.saveDevice(device.copy(friendlyName = name, connectPort = port))) {
                is NexusResult.Success -> messageChannel.send(UiMessage.Text(R.string.saved_message))
                is NexusResult.Failure -> messageChannel.send(UiMessage.Error(result.errorCode))
            }
        }
        return true
    }

    fun reconnect(device: TvDevice) {
        if (busyDeviceId.value != null) return
        viewModelScope.launch {
            busyDeviceId.value = device.id
            when (val result = container.connectTv(device)) {
                is NexusResult.Success ->
                    messageChannel.send(UiMessage.Text(R.string.home_connected_message, listOf(result.value.friendlyName)))
                is NexusResult.Failure -> messageChannel.send(UiMessage.Error(result.errorCode))
            }
            busyDeviceId.value = null
        }
    }

    fun forget(device: TvDevice) {
        viewModelScope.launch {
            container.forgetDevice(device.id)
            messageChannel.send(UiMessage.Text(R.string.forgot_message, listOf(device.friendlyName)))
        }
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setHapticsEnabled(enabled) }
    fun setSound(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setSoundEnabled(enabled) }
    fun setConfirmPower(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setConfirmPowerOff(enabled) }

    fun onLocalNetworkDenied() {
        messageChannel.trySend(UiMessage.Text(R.string.error_local_network_denied))
    }
}
