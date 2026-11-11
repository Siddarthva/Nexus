package com.example.nexusremote.ui.screens.tvsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.R
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.command.OpenSettingsCommand
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
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

data class TvSettingsUiState(
    val connected: Boolean = false,
    /** The command currently being sent; further taps are ignored until it finishes. */
    val opening: OpenSettingsCommand? = null,
)

/**
 * Opens fixed TV settings pages. One command per tap, never retried, never sent automatically.
 * The only input is an [OpenSettingsCommand] enum value; no UI string reaches the TV.
 */
class TvSettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val opening = MutableStateFlow<OpenSettingsCommand?>(null)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<TvSettingsUiState> = combine(container.tvClient.connectionState, opening) { connection, busy ->
        TvSettingsUiState(connected = connection is ConnectionState.Connected, opening = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvSettingsUiState())

    /** User tapped a settings button. */
    fun open(command: OpenSettingsCommand) {
        if (!uiState.value.connected || opening.value != null) return
        viewModelScope.launch {
            opening.value = command
            when (val result = container.openTvSettings(command)) {
                is NexusResult.Success -> messageChannel.send(UiMessage.Text(R.string.tvsettings_opened))
                is NexusResult.Failure -> messageChannel.send(
                    // "The TV did not open it" gets the friendly message; connection problems keep theirs.
                    if (result.errorCode == ErrorCode.UNSUPPORTED || result.errorCode == ErrorCode.COMMAND_FAILED) {
                        UiMessage.Text(R.string.tvsettings_unavailable)
                    } else {
                        UiMessage.Error(result.errorCode)
                    },
                )
            }
            opening.value = null
        }
    }
}
