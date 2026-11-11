package com.example.nexusremote.ui.screens.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ConfirmDialog
import com.example.nexusremote.ui.components.ConnectionIndicator
import com.example.nexusremote.ui.components.DPad
import com.example.nexusremote.ui.components.GradientButton
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.ui.components.rememberLocalNetworkGate
import com.example.nexusremote.ui.components.rememberPressFeedback
import com.example.nexusremote.ui.components.resolve
import com.example.nexusremote.ui.navigation.Routes

@Composable
fun RemoteScreen(
    snackbarHostState: SnackbarHostState,
    onOpenApps: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenKeyboard: () -> Unit = {},
    onOpenTvSettings: () -> Unit = {},
    viewModel: RemoteViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedback = rememberPressFeedback(state.settings.hapticsEnabled, state.settings.soundEnabled)
    val lanGate = rememberLocalNetworkGate(onDenied = viewModel::onLocalNetworkDenied)

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    val press: (TvCommand) -> Unit = { command ->
        feedback()
        viewModel.press(command)
    }
    val repeat: (TvCommand) -> Unit = { command ->
        feedback()
        viewModel.repeat(command)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            connection = state.connection,
            powerEnabled = state.controlsEnabled,
            onPower = { press(TvCommand.POWER) },
            onOpenApps = onOpenApps,
            onOpenKeyboard = onOpenKeyboard,
            onOpenTvSettings = onOpenTvSettings,
        )
        Text(
            text = state.device?.friendlyName ?: stringResource(R.string.remote_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )

        if (state.device == null) {
            SectionCard { BodyText(stringResource(R.string.remote_no_tv)) }
        } else if (!state.controlsEnabled) {
            DisconnectedBanner(
                connection = state.connection,
                reconnecting = state.reconnecting || state.connection == ConnectionState.Connecting,
                onReconnect = { lanGate { viewModel.reconnect() } },
                onRepair = { state.device?.let { onNavigate(Routes.pairCode(it.host, it.id)) } },
            )
        }

        DPad(
            enabled = state.controlsEnabled,
            onPress = press,
            onRepeat = repeat,
            onRelease = viewModel::release,
            modifier = Modifier.fillMaxWidth(),
        )

        NavigationRow(enabled = state.controlsEnabled, onPress = press, onRelease = viewModel::release)
        VolumeRow(enabled = state.controlsEnabled, onPress = press, onRepeat = repeat, onRelease = viewModel::release)
        MediaRow(enabled = state.controlsEnabled, onPress = press, onRelease = viewModel::release)

        if (state.quickLaunch.isNotEmpty()) {
            QuickLaunchRow(
                apps = state.quickLaunch,
                enabled = state.controlsEnabled,
                launchingId = state.launchingId,
                onLaunch = { app ->
                    feedback()
                    viewModel.launchQuickApp(app)
                },
            )
        }
        Spacer(Modifier.size(4.dp))
    }

    if (state.showPowerConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.power_confirm_title),
            body = stringResource(R.string.power_confirm_body),
            confirmLabel = stringResource(R.string.power_confirm_action),
            onConfirm = viewModel::confirmPower,
            onDismiss = viewModel::dismissPower,
            destructive = true,
        )
    }
}

@Composable
private fun DisconnectedBanner(
    connection: ConnectionState,
    reconnecting: Boolean,
    onReconnect: () -> Unit,
    onRepair: () -> Unit,
) {
    val needsRepair = connection is ConnectionState.Failed &&
        (connection.errorCode == ErrorCode.AUTH_REVOKED || connection.errorCode == ErrorCode.NOT_PAIRED)
    SectionCard {
        BodyText(stringResource(R.string.remote_disconnected_banner))
        when {
            reconnecting -> CircularProgressIndicator(Modifier.size(28.dp))
            needsRepair -> GradientButton(
                text = stringResource(R.string.home_repair),
                onClick = onRepair,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> GradientButton(
                text = stringResource(R.string.remote_reconnect),
                onClick = onReconnect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Connection pill on the left; More menu and Power on the right. */
@Composable
private fun TopBar(
    connection: ConnectionState,
    powerEnabled: Boolean,
    onPower: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenTvSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) { ConnectionIndicator(state = connection) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem(R.string.nav_apps, Icons.Rounded.Apps) { menuOpen = false; onOpenApps() }
                MenuItem(R.string.nav_keyboard, Icons.Rounded.Keyboard) { menuOpen = false; onOpenKeyboard() }
                MenuItem(R.string.tvsettings_title, Icons.Rounded.Tune) { menuOpen = false; onOpenTvSettings() }
            }
        }
        PowerButton(enabled = powerEnabled, onPress = onPower)
    }
}

@Composable
private fun MenuItem(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
