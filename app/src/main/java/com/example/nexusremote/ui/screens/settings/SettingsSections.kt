package com.example.nexusremote.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.QuickLaunchConfig
import com.example.nexusremote.domain.model.TvDevice
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ConnectionIndicator
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.util.InputValidators

/** A saved TV: name, address, status, and Rename / Forget. */
@Composable
fun SavedTvRow(
    device: TvDevice,
    isActive: Boolean,
    connection: ConnectionState,
    onRename: () -> Unit,
    onForget: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.friendlyName, style = MaterialTheme.typography.titleMedium)
                BodyText(device.host)
            }
            if (isActive) {
                val shown = if (connection is ConnectionState.Connected && connection.deviceId != device.id) {
                    ConnectionState.Disconnected
                } else {
                    connection
                }
                ConnectionIndicator(shown)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRename) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_edit_tv, device.friendlyName))
            }
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_forget_tv, device.friendlyName),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Rename a TV. (Ports are kept up to date by discovery, so they are not edited here.) */
@Composable
fun RenameTvDialog(device: TvDevice, onSave: (name: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(device.id) { mutableStateOf(device.friendlyName) }
    val nameError = !InputValidators.isValidFriendlyName(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_tv_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(InputValidators.MAX_FRIENDLY_NAME_LENGTH) },
                label = { Text(stringResource(R.string.pair_field_name)) },
                isError = nameError,
                supportingText = if (nameError) ({ Text(stringResource(R.string.pair_error_name)) }) else null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = !nameError) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun PrivacySection() {
    SectionCard(title = stringResource(R.string.privacy_title)) {
        BodyText(stringResource(R.string.privacy_body))
    }
}

/** Show/hide and reorder the Remote screen's quick-launch apps. Saved in DataStore. */
@Composable
fun QuickLaunchSection(
    config: QuickLaunchConfig,
    onVisibleChange: (id: String, visible: Boolean) -> Unit,
    onMove: (id: String, delta: Int) -> Unit,
) {
    val apps = config.ordered
    SectionCard(title = stringResource(R.string.quick_launch_title)) {
        BodyText(stringResource(R.string.settings_quick_launch_desc))
        apps.forEachIndexed { index, app ->
            val visible = app.id !in config.hidden
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .toggleable(value = visible, onValueChange = { onVisibleChange(app.id, it) }, role = Role.Switch),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Switch(checked = visible, onCheckedChange = null)
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { onMove(app.id, -1) }, enabled = index > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_up, app.label))
                }
                IconButton(onClick = { onMove(app.id, 1) }, enabled = index < apps.lastIndex) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_down, app.label))
                }
            }
        }
    }
}
