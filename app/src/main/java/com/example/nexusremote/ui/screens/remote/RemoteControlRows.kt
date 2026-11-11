package com.example.nexusremote.ui.screens.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.nexusremote.domain.model.QuickLaunchApp
import com.example.nexusremote.ui.components.ControlGroup
import com.example.nexusremote.ui.components.pressScale
import com.example.nexusremote.ui.theme.NexusGradients
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.nexusremote.R
import com.example.nexusremote.domain.command.TvCommand
import com.example.nexusremote.ui.components.RemoteIconButton

@Composable
fun PowerButton(enabled: Boolean, onPress: () -> Unit) {
    RemoteIconButton(
        icon = Icons.Rounded.PowerSettingsNew,
        contentDescription = stringResource(R.string.cd_power),
        enabled = enabled,
        onPress = onPress,
        // POWER fires only on a completed tap (finger up). A scroll that happens to start on
        // this button sends nothing.
        fireOnTapOnly = true,
        size = 56.dp,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconTint = MaterialTheme.colorScheme.error,
    )
}

/** Back and Home. (Apps is in the Remote screen's "More" menu.) */
@Composable
fun NavigationRow(
    enabled: Boolean,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
) {
    ControlGroup {
        RemoteIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            enabled = enabled,
            onPress = { onPress(TvCommand.BACK) },
            onRelease = onRelease,
        )
        RemoteIconButton(
            icon = Icons.Rounded.Home,
            contentDescription = stringResource(R.string.cd_home),
            enabled = enabled,
            onPress = { onPress(TvCommand.HOME) },
            onRelease = onRelease,
        )
    }
}

/** Rewind, play/pause, fast-forward. */
@Composable
fun MediaRow(enabled: Boolean, onPress: (TvCommand) -> Unit, onRelease: () -> Unit) {
    ControlGroup {
        RemoteIconButton(
            icon = Icons.Rounded.FastRewind,
            contentDescription = stringResource(R.string.cd_rewind),
            enabled = enabled,
            onPress = { onPress(TvCommand.REWIND) },
            onRelease = onRelease,
        )
        RemoteIconButton(
            icon = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.cd_play_pause),
            enabled = enabled,
            onPress = { onPress(TvCommand.PLAY_PAUSE) },
            onRelease = onRelease,
            size = 72.dp,
            containerColor = MaterialTheme.colorScheme.primary,
            iconTint = MaterialTheme.colorScheme.onPrimary,
        )
        RemoteIconButton(
            icon = Icons.Rounded.FastForward,
            contentDescription = stringResource(R.string.cd_fast_forward),
            enabled = enabled,
            onPress = { onPress(TvCommand.FAST_FORWARD) },
            onRelease = onRelease,
        )
    }
}

/** Volume down / mute / volume up in one pill. Volume keys repeat while held. */
@Composable
fun VolumeRow(
    enabled: Boolean,
    onPress: (TvCommand) -> Unit,
    onRepeat: (TvCommand) -> Unit,
    onRelease: () -> Unit,
) {
    ControlGroup {
        RemoteIconButton(
            icon = Icons.Rounded.Remove,
            contentDescription = stringResource(R.string.cd_volume_down),
            enabled = enabled,
            onPress = { onPress(TvCommand.VOLUME_DOWN) },
            repeatable = true,
            onRepeat = { onRepeat(TvCommand.VOLUME_DOWN) },
            onRelease = onRelease,
            containerColor = Color.Transparent,
        )
        Text(
            text = stringResource(R.string.remote_volume),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
        RemoteIconButton(
            icon = Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = stringResource(R.string.cd_mute),
            enabled = enabled,
            onPress = { onPress(TvCommand.MUTE) },
            onRelease = onRelease,
            size = 56.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        RemoteIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.cd_volume_up),
            enabled = enabled,
            onPress = { onPress(TvCommand.VOLUME_UP) },
            repeatable = true,
            onRepeat = { onRepeat(TvCommand.VOLUME_UP) },
            onRelease = onRelease,
            containerColor = Color.Transparent,
        )
    }
}

/**
 * Quick launch: one tile per app from the fixed list, in the user's order. Each tile fires on
 * a completed tap (Material click), so a scroll that starts on a tile launches nothing.
 */
@Composable
fun QuickLaunchRow(
    apps: List<QuickLaunchApp>,
    enabled: Boolean,
    launchingId: String?,
    onLaunch: (QuickLaunchApp) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.quick_launch_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            apps.forEach { app ->
                QuickLaunchTile(
                    app = app,
                    enabled = enabled && launchingId == null,
                    busy = launchingId == app.id,
                    onClick = { onLaunch(app) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickLaunchTile(
    app: QuickLaunchApp,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(R.string.cd_quick_launch, app.label)
    Card(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 88.dp).pressScale(interaction),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, NexusGradients.cardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(brandColor(app.id), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(app.label.take(1), style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Decorative tile colours (plain colours, no brand logos). */
private fun brandColor(id: String): Color = when (id) {
    "youtube" -> Color(0xFFD93025)
    "netflix" -> Color(0xFFB0060F)
    "prime_video" -> Color(0xFF1A98FF)
    else -> Color(0xFF6E56CF)
}

