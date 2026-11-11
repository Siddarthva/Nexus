package com.example.nexusremote.ui.screens.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.R
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.ui.components.UiMessage
import com.example.nexusremote.util.NexusResult
import com.example.nexusremote.util.ShellTextEscaper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KeyboardUiState(
    val text: String = "",
    val sending: Boolean = false,
    val connected: Boolean = false,
) {
    /** Unsupported characters in the current text, shown inline before sending. */
    val unsupported: List<Char> get() = text.filterNot(ShellTextEscaper::isSupported).toList().distinct()
    val canSend: Boolean get() = connected && !sending && text.isNotEmpty() && unsupported.isEmpty()
}

class KeyboardViewModel(private val container: AppContainer) : ViewModel() {

    private val text = MutableStateFlow("")
    private val sending = MutableStateFlow(false)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<KeyboardUiState> = combine(
        text,
        sending,
        container.tvClient.connectionState,
    ) { t, s, connection ->
        KeyboardUiState(text = t, sending = s, connected = connection is ConnectionState.Connected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeyboardUiState())

    fun onTextChange(value: String) {
        // Allow a little over the limit so the user sees the "too long" message instead of silent truncation.
        text.value = value.take(ShellTextEscaper.MAX_LENGTH + 20)
    }

    fun clear() {
        text.value = ""
    }

    fun send() {
        val value = text.value
        when (val outcome = ShellTextEscaper.escape(value)) {
            ShellTextEscaper.Outcome.Empty -> {
                messageChannel.trySend(UiMessage.Text(R.string.keyboard_empty))
                return
            }
            ShellTextEscaper.Outcome.TooLong -> {
                messageChannel.trySend(UiMessage.Text(R.string.keyboard_too_long))
                return
            }
            is ShellTextEscaper.Outcome.Unsupported -> {
                messageChannel.trySend(UiMessage.Text(R.string.keyboard_unsupported, listOf(outcome.rejected.joinToString(" "))))
                return
            }
            is ShellTextEscaper.Outcome.Escaped -> Unit
        }
        if (sending.value) return
        viewModelScope.launch {
            sending.value = true
            when (val result = container.sendText(value)) {
                is NexusResult.Success -> messageChannel.send(UiMessage.Text(R.string.keyboard_sent))
                is NexusResult.Failure -> messageChannel.send(UiMessage.Error(result.errorCode))
            }
            sending.value = false
        }
    }
}
