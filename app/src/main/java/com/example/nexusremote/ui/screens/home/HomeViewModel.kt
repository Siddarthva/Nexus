package com.example.nexusremote.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.R
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.ui.components.UiMessage
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

data class HomeUiState(
    val loaded: Boolean = false,
    val activeDevice: TvDevice? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val busy: Boolean = false,
    val items: List<TvListItem> = emptyList(),
    val scanning: Boolean = false,
    /** Key of the TV currently being connected from the list, if any. */
    val connectingKey: String? = null,
) {
    val isConnectedToActive: Boolean
        get() = connection is ConnectionState.Connected && connection.deviceId == activeDevice?.id

    fun isConnected(item: TvListItem): Boolean =
        item.deviceId != null && connection is ConnectionState.Connected && connection.deviceId == item.deviceId
}

/** One-off navigation requests from the TV list. */
sealed interface HomeEvent {
    data object OpenRemote : HomeEvent
    data class Pair(val host: String, val deviceId: String?) : HomeEvent
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val connectingKey = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    /** Saved TVs that still have a stored pairing key. */
    private val pairedDevices: Flow<List<TvDevice>> = container.deviceRepository.devices.map { list ->
        list.filter { container.credentialStore.hasCredentials(it.id) }
    }

    private val list = combine(
        container.tvDiscovery.tvs,
        container.tvDiscovery.scanning,
        pairedDevices,
        connectingKey,
    ) { tvs, scanning, paired, connecting -> Triple(buildTvList(tvs, paired), scanning, connecting) }

    val uiState: StateFlow<HomeUiState> = combine(
        container.deviceRepository.activeDevice,
        container.tvClient.connectionState,
        busy,
        list,
    ) { device, connection, isBusy, (items, scanning, connecting) ->
        HomeUiState(
            loaded = true,
            activeDevice = device,
            connection = connection,
            busy = isBusy,
            items = items,
            scanning = scanning,
            connectingKey = connecting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** The TV list is visible: scan (discovery only listens; nothing is sent to any TV). */
    fun startScanning() = container.tvDiscovery.acquire()

    fun stopScanning() = container.tvDiscovery.release()

    fun refresh() = container.tvDiscovery.refresh()

    /** User tapped a TV in the list. */
    fun onTvTapped(item: TvListItem) {
        val state = uiState.value
        if (state.isConnected(item)) {
            eventChannel.trySend(HomeEvent.OpenRemote)
            return
        }
        if (connectingKey.value != null) return
        val deviceId = item.deviceId
        if (deviceId == null) {
            // Not paired yet: open the code-only pairing screen for this TV.
            eventChannel.trySend(HomeEvent.Pair(item.host, null))
            return
        }
        viewModelScope.launch {
            connectingKey.value = item.key
            val saved = container.deviceRepository.getDevice(deviceId)
            if (saved != null) {
                // Ports and IP come from discovery when the TV is visible on the network.
                val target = saved.copy(
                    host = item.host,
                    connectPort = item.connectPort ?: saved.connectPort,
                    mdnsId = item.mdnsId ?: saved.mdnsId,
                )
                when (val result = container.connectTv(target)) {
                    is NexusResult.Success -> eventChannel.send(HomeEvent.OpenRemote)
                    is NexusResult.Failure -> {
                        messageChannel.send(UiMessage.Error(result.errorCode))
                        val trustLost = result.errorCode == ErrorCode.AUTH_REVOKED || result.errorCode == ErrorCode.NOT_PAIRED
                        if (trustLost && item.onNetwork) eventChannel.send(HomeEvent.Pair(item.host, deviceId))
                    }
                }
            }
            connectingKey.value = null
        }
    }

    /** Reconnects the active TV (status card). */
    fun connect() {
        val device = uiState.value.activeDevice ?: return
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            when (val result = container.connectTv(device)) {
                is NexusResult.Success ->
                    messageChannel.send(UiMessage.Text(R.string.home_connected_message, listOf(result.value.friendlyName)))
                is NexusResult.Failure -> messageChannel.send(UiMessage.Error(result.errorCode))
            }
            busy.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch { container.tvClient.disconnect() }
    }

    fun onLocalNetworkDenied() {
        messageChannel.trySend(UiMessage.Text(R.string.error_local_network_denied))
    }
}
