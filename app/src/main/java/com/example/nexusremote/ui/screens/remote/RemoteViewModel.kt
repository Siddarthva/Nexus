package com.example.nexusremote.ui.screens.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.R
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.command.CommandQueue
import com.example.nexusremote.domain.command.KeyCodeMapping
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.AppSettings
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.QuickLaunchApp
import com.example.nexusremote.domain.model.QuickLaunchApps
import com.example.nexusremote.domain.model.QuickLaunchConfig
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.ui.components.UiMessage
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

data class RemoteUiState(
    val device: TvDevice? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val settings: AppSettings = AppSettings(),
    val showPowerConfirm: Boolean = false,
    val reconnecting: Boolean = false,
    /** Visible quick-launch apps in the user's order (from the fixed list). */
    val quickLaunch: List<QuickLaunchApp> = QuickLaunchConfig().visible,
    /** Id of the quick-launch app being opened; further launches wait until it finishes. */
    val launchingId: String? = null,
) {
    /** Controls work only when the active TV is the connected one. */
    val controlsEnabled: Boolean
        get() = device != null && connection is ConnectionState.Connected && connection.deviceId == device.id
}

class RemoteViewModel(private val container: AppContainer) : ViewModel() {

    private val showPowerConfirm = MutableStateFlow(false)
    private val reconnecting = MutableStateFlow(false)
    private val launchingId = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val queue = CommandQueue(viewModelScope) { command -> container.sendCommand(command) }

    private val extras = combine(
        container.quickLaunchPreferences.config,
        launchingId,
    ) { config, launching -> config.visible to launching }

    val uiState: StateFlow<RemoteUiState> = combine(
        container.deviceRepository.activeDevice,
        container.tvClient.connectionState,
        container.settingsRepository.settings,
        combine(showPowerConfirm, reconnecting) { power, busy -> power to busy },
        extras,
    ) { device, connection, settings, (power, busy), (quick, launching) ->
        RemoteUiState(device, connection, settings, power, busy, quick, launching)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteUiState())

    init {
        viewModelScope.launch {
            queue.outcomes.collect { outcome ->
                val result = outcome.result
                // Every failure is surfaced; nothing is dropped silently.
                if (result is NexusResult.Failure) messageChannel.send(UiMessage.Error(result.errorCode))
            }
        }
    }

    /** Finger down (or accessibility click): sends one command. */
    fun press(command: TvCommand) {
        if (!uiState.value.controlsEnabled) return
        if (command == TvCommand.POWER && uiState.value.settings.confirmPowerOff) {
            showPowerConfirm.value = true
            return
        }
        queue.enqueue(command)
    }

    /** Called every ~150 ms while a repeatable button is held. */
    fun repeat(command: TvCommand) {
        if (!uiState.value.controlsEnabled || !KeyCodeMapping.isRepeatable(command)) return
        queue.enqueueRepeat(command)
    }

    /** Finger lifted: drop any repeats still waiting in the queue. */
    fun release() = queue.cancelRepeats()

    /**
     * User tapped a quick-launch button. Only apps from the fixed [QuickLaunchApps] list can be
     * launched, and only through LaunchAppUseCase (strict package-name check). One attempt per
     * tap, no retry.
     */
    fun launchQuickApp(app: QuickLaunchApp) {
        if (!uiState.value.controlsEnabled || launchingId.value != null) return
        val fixed = QuickLaunchApps.byId(app.id) ?: return
        viewModelScope.launch {
            launchingId.value = fixed.id
            val result = container.launchApp(fixed.packageName)
            if (result is NexusResult.Failure) messageChannel.send(UiMessage.Text(R.string.quick_launch_failed))
            launchingId.value = null
        }
    }

    fun confirmPower() {
        showPowerConfirm.value = false
        if (uiState.value.controlsEnabled) queue.enqueue(TvCommand.POWER)
    }

    fun dismissPower() {
        showPowerConfirm.value = false
    }

    fun reconnect() {
        val device = uiState.value.device ?: return
        if (reconnecting.value) return
        viewModelScope.launch {
            reconnecting.value = true
            val result = container.connectTv(device)
            if (result is NexusResult.Failure) messageChannel.send(UiMessage.Error(result.errorCode))
            reconnecting.value = false
        }
    }

    fun onLocalNetworkDenied() {
        messageChannel.trySend(UiMessage.Text(R.string.error_local_network_denied))
    }
}
