package com.example.nexusremote.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.rememberLocalNetworkPermissionState
import com.example.nexusremote.ui.components.resolve
import com.example.nexusremote.ui.navigation.Routes

/**
 * Home = the TV list. Large connection status on top, then TVs discovered on the network
 * (plus paired TVs that are not visible right now), and a manual-IP fallback at the bottom.
 */
@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLocalNetworkPermissionState()
    var askedForPermission by rememberSaveable { mutableStateOf(false) }

    // Android 17+: ask for local-network access once, before the first scan.
    LaunchedEffect(permission.granted) {
        if (!permission.granted && !askedForPermission) {
            askedForPermission = true
            permission.request()
        }
    }
    // Scan only while this screen is shown and access is granted. The app-level lifecycle
    // additionally pauses scanning whenever the app is in the background.
    if (permission.granted) {
        DisposableEffect(viewModel) {
            viewModel.startScanning()
            onDispose { viewModel.stopScanning() }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.OpenRemote -> onNavigate(Routes.REMOTE)
                is HomeEvent.Pair -> onNavigate(Routes.pairCode(event.host, event.deviceId))
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ConnectionStatusCard(
                state = state,
                onOpenRemote = { onNavigate(Routes.REMOTE) },
                onDisconnect = viewModel::disconnect,
            )
        }
        if (!permission.granted && permission.denied) {
            item { PermissionCard(onRetry = permission.request) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_tvs_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = viewModel::refresh, enabled = permission.granted) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cd_refresh_tvs))
                }
            }
            if (state.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                BodyText(stringResource(R.string.home_scanning))
            }
        }
        items(state.items, key = { it.key }) { item ->
            TvCard(
                item = item,
                connected = state.isConnected(item),
                connecting = state.connectingKey == item.key,
                onClick = { viewModel.onTvTapped(item) },
            )
        }
        if (state.items.isEmpty() && permission.granted) {
            item { EmptyState(scanning = state.scanning) }
        }
        item {
            TextButton(
                onClick = { onNavigate(Routes.pair()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.home_manual))
            }
        }
    }
}
