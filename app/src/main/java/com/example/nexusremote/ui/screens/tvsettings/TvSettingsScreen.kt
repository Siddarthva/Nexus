package com.example.nexusremote.ui.screens.tvsettings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexusremote.R
import com.example.nexusremote.domain.command.OpenSettingsCommand
import com.example.nexusremote.ui.AppViewModelProvider
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.ScreenHeader
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.ui.components.pressScale
import com.example.nexusremote.ui.components.resolve
import com.example.nexusremote.ui.theme.NexusGradients

/** One tile per fixed settings page. The label is for display only; the enum is what is sent. */
private data class SettingsTile(val command: OpenSettingsCommand, val labelRes: Int, val icon: ImageVector)

private val TILES = listOf(
    SettingsTile(OpenSettingsCommand.WIFI, R.string.tvsettings_wifi, Icons.Rounded.Wifi),
    SettingsTile(OpenSettingsCommand.BLUETOOTH, R.string.tvsettings_bluetooth, Icons.Rounded.Bluetooth),
    SettingsTile(OpenSettingsCommand.DISPLAY, R.string.tvsettings_display, Icons.Rounded.Tv),
    SettingsTile(OpenSettingsCommand.SOUND, R.string.tvsettings_sound, Icons.AutoMirrored.Rounded.VolumeUp),
    SettingsTile(OpenSettingsCommand.APPS, R.string.tvsettings_apps, Icons.Rounded.Apps),
    SettingsTile(OpenSettingsCommand.DEVICE_INFO, R.string.tvsettings_device_info, Icons.Rounded.Info),
)

@Composable
fun TvSettingsScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: TvSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.tvsettings_title), onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                BodyText(stringResource(R.string.tvsettings_note))
                if (!state.connected) {
                    Text(
                        stringResource(R.string.tvsettings_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TILES.chunked(2).forEach { row ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { tile ->
                        SettingsTileCard(
                            tile = tile,
                            enabled = state.connected && state.opening == null,
                            busy = state.opening == tile.command,
                            onClick = { viewModel.open(tile.command) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTileCard(
    tile: SettingsTile,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val label = stringResource(tile.labelRes)
    val description = stringResource(R.string.cd_tvsettings_open, label)
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 112.dp).pressScale(interaction),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, NexusGradients.cardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(NexusGradients.accentWash, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(tile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
