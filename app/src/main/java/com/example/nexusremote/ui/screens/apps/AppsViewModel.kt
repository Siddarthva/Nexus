package com.example.nexusremote.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.R
import com.example.nexusremote.app.AppContainer
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.domain.model.TvApp
import com.example.nexusremote.ui.components.UiMessage
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppsUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val query: String = "",
    val favorites: List<TvApp> = emptyList(),
    val others: List<TvApp> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val loadError: ErrorCode? = null,
    val loadedOnce: Boolean = false,
)

class AppsViewModel(private val container: AppContainer) : ViewModel() {

    private val apps = MutableStateFlow<List<TvApp>>(emptyList())
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(false)
    private val loadError = MutableStateFlow<ErrorCode?>(null)
    private val loadedOnce = MutableStateFlow(false)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val connected: Flow<Boolean> = container.tvClient.connectionState
        .map { it is ConnectionState.Connected }
        .distinctUntilChanged()

    private val status = combine(loading, loadError, loadedOnce) { l, e, once -> Triple(l, e, once) }

    val uiState: StateFlow<AppsUiState> = combine(
        apps,
        query,
        container.settingsRepository.favoritePackages,
        connected,
        status,
    ) { all, q, favs, isConnected, (isLoading, error, once) ->
        val filtered = if (q.isBlank()) all else all.filter {
            it.label.contains(q.trim(), ignoreCase = true) || it.packageName.contains(q.trim(), ignoreCase = true)
        }
        AppsUiState(
            connected = isConnected,
            loading = isLoading,
            query = q,
            favorites = filtered.filter { it.packageName in favs },
            others = filtered.filterNot { it.packageName in favs },
            favoriteIds = favs,
            loadError = error,
            loadedOnce = once,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        // Load automatically the first time the TV is connected while this screen exists.
        viewModelScope.launch {
            connected.collect { isConnected -> if (isConnected && !loadedOnce.value) refresh() }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value.take(60)
    }

    fun refresh() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            when (val result = container.listApps()) {
                is NexusResult.Success -> {
                    apps.value = result.value
                    loadError.value = null
                    loadedOnce.value = true
                }
                is NexusResult.Failure -> loadError.value = result.errorCode
            }
            loading.value = false
        }
    }

    fun launch(app: TvApp) {
        viewModelScope.launch {
            messageChannel.send(UiMessage.Text(R.string.apps_opening, listOf(app.label)))
            val result = container.launchApp(app.packageName)
            if (result is NexusResult.Failure) {
                messageChannel.send(UiMessage.TextWithError(R.string.apps_launch_failed, app.label, result.errorCode))
            }
        }
    }

    fun toggleFavorite(app: TvApp) {
        viewModelScope.launch { container.settingsRepository.toggleFavorite(app.packageName) }
    }
}
