package com.example.nexusremote.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.example.nexusremote.ui.components.ConnectionIndicator
import com.example.nexusremote.ui.components.GradientButton
import com.example.nexusremote.ui.components.pressScale
import com.example.nexusremote.ui.theme.NexusGradients
import com.example.nexusremote.ui.theme.NexusOnPurple
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.ui.components.BodyText
import com.example.nexusremote.ui.components.SectionCard
import com.example.nexusremote.ui.components.openAppSettings
import com.example.nexusremote.ui.theme.NexusSuccess

/** Large connection status at the top of the TV list. */
@Composable
fun ConnectionStatusCard(state: HomeUiState, onOpenRemote: () -> Unit, onDisconnect: () -> Unit) {
    val connection = state.connection
    val connectedId = (connection as? ConnectionState.Connected)?.deviceId
    val connectedDevice = state.activeDevice?.takeIf { it.id == connectedId }
    val connected = connectedId != null
    val title = when {
        connected -> connectedDevice?.friendlyName ?: stringResource(R.string.tv_unnamed_generic)
        connection == ConnectionState.Connecting || state.connectingKey != null -> stringResource(R.string.home_status_connecting)
        connection is ConnectionState.Failed &&
            (connection.errorCode == ErrorCode.AUTH_REVOKED || connection.errorCode == ErrorCode.NOT_PAIRED) ->
            stringResource(R.string.status_pairing_required)
        else -> stringResource(R.string.home_status_not_connected)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, if (connected) NexusGradients.accent else NexusGradients.cardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (connected) NexusGradients.accentWash else SolidColor(Color.Transparent))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status pill with a coloured dot (announced by TalkBack when it changes).
            ConnectionIndicator(state = connection)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).background(
                        if (connected) NexusGradients.accent else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Rounded.Tv else Icons.Rounded.LinkOff,
                        contentDescription = null,
                        tint = if (connected) NexusOnPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (connectedDevice != null) {
                        Text(
                            text = connectedDevice.host,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton(
                        text = stringResource(R.string.home_open_remote),
                        onClick = onOpenRemote,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.home_disconnect), style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                BodyText(stringResource(R.string.home_status_hint))
            }
        }
    }
}

/** One TV in the list. The whole card is the tap target. */
@Composable
fun TvCard(item: TvListItem, connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val name = item.name
        ?: item.idHint?.let { stringResource(R.string.tv_unnamed, it.take(16)) }
        ?: stringResource(R.string.tv_unnamed_generic)
    val status = stringResource(
        when {
            connected -> R.string.tv_status_connected
            !item.onNetwork -> R.string.tv_status_offline
            item.paired -> R.string.tv_status_paired
            item.pairingPort != null -> R.string.tv_status_ready_to_pair
            else -> R.string.tv_status_new
        },
    )
    val dotColor = when {
        connected -> NexusSuccess
        item.onNetwork -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).pressScale(interaction),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, NexusGradients.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(NexusGradients.accentWash, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                BodyText(item.host)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                    Text(text = status, style = MaterialTheme.typography.labelMedium, color = dotColor)
                }
            }
            when {
                connecting -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                connected -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = NexusSuccess)
            }
        }
    }
}

/** Shown when no TV has been found yet. */
@Composable
fun EmptyState(scanning: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(if (scanning) R.string.home_empty_title else R.string.home_empty_idle)) {
        BodyText(stringResource(R.string.home_empty_body))
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.home_how_to))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            listOf(R.string.pair_step_haier_1, R.string.pair_step_haier_2, R.string.pair_step_haier_3).forEach {
                Text(stringResource(R.string.bullet_item, stringResource(it)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Android 17+: local-network access was denied. */
@Composable
fun PermissionCard(onRetry: () -> Unit) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.perm_title)) {
        BodyText(stringResource(R.string.perm_body))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.perm_retry))
            }
            OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.perm_settings))
            }
        }
    }
}
