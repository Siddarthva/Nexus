package com.example.nexusremote.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ConfirmDialog
import com.example.nexusremote.ui.components.ScreenHeader
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.ui.components.resolve
import com.example.nexusremote.ui.navigation.Routes

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<TvDevice?>(null) }
    var forgetting by remember { mutableStateOf<TvDevice?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.settings_title))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_saved_tvs)) {
                if (state.devices.isEmpty()) BodyText(stringResource(R.string.settings_no_tvs))
                state.devices.forEach { device ->
                    SavedTvRow(
                        device = device,
                        isActive = device.id == state.activeDeviceId,
                        connection = state.connection,
                        onRename = { editing = device },
                        onForget = { forgetting = device },
                    )
                }
                OutlinedButton(
                    // New TVs are added from the TV list (discovery); manual IP entry is a fallback there.
                    onClick = { onNavigate(Routes.HOME) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.settings_add_tv))
                }
            }

            SectionCard(title = stringResource(R.string.settings_preferences)) {
                ToggleRow(
                    title = stringResource(R.string.settings_haptics),
                    description = stringResource(R.string.settings_haptics_desc),
                    checked = state.settings.hapticsEnabled,
                    onCheckedChange = { viewModel.setHaptics(it) },
                )
                ToggleRow(
                    title = stringResource(R.string.settings_power_confirm),
                    description = stringResource(R.string.settings_power_confirm_desc),
                    checked = state.settings.confirmPowerOff,
                    onCheckedChange = { viewModel.setConfirmPower(it) },
                )
            }

            QuickLaunchSection(
                config = state.quickLaunch,
                onVisibleChange = { id, visible -> viewModel.setQuickLaunchVisible(id, visible) },
                onMove = { id, delta -> viewModel.moveQuickLaunch(id, delta) },
            )
            PrivacySection()
        }
    }

    editing?.let { device ->
        RenameTvDialog(
            device = device,
            // The connect port is kept as-is; discovery updates it automatically.
            onSave = { name -> if (viewModel.saveEdit(device, name, device.connectPort.toString())) editing = null },
            onDismiss = { editing = null },
        )
    }
    forgetting?.let { device ->
        ConfirmDialog(
            title = stringResource(R.string.forget_title, device.friendlyName),
            body = stringResource(R.string.forget_body),
            confirmLabel = stringResource(R.string.forget_confirm),
            onConfirm = {
                viewModel.forget(device)
                forgetting = null
            },
            onDismiss = { forgetting = null },
            destructive = true,
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            BodyText(description)
        }
        // The whole row is the toggle; the switch is visual only.
        Switch(checked = checked, onCheckedChange = null)
    }
}
