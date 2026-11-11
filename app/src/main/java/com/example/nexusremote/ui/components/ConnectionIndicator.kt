package com.example.nexusremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import com.example.nexusremote.R
import com.example.nexusremote.domain.model.ConnectionState
import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.ui.theme.NexusError
import com.example.nexusremote.ui.theme.NexusOnSurfaceMuted
import com.example.nexusremote.ui.theme.NexusSuccess
import com.example.nexusremote.ui.theme.NexusWarning

@Composable
fun connectionLabel(state: ConnectionState): String = stringResource(
    when (state) {
        is ConnectionState.Connected -> R.string.status_connected
        ConnectionState.Connecting -> R.string.status_connecting
        ConnectionState.Pairing -> R.string.status_pairing
        ConnectionState.Disconnected -> R.string.status_disconnected
        is ConnectionState.Failed -> when (state.errorCode) {
            ErrorCode.NOT_PAIRED, ErrorCode.AUTH_REVOKED -> R.string.status_pairing_required
            ErrorCode.UNSUPPORTED -> R.string.status_unsupported
            else -> R.string.status_failed
        }
    },
)

private fun connectionColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> NexusSuccess
    ConnectionState.Connecting, ConnectionState.Pairing -> NexusWarning
    ConnectionState.Disconnected -> NexusOnSurfaceMuted
    is ConnectionState.Failed -> NexusError
}

/** Pill showing the live connection state. Announced by TalkBack when it changes. */
@Composable
fun ConnectionIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    val label = connectionLabel(state)
    val description = stringResource(R.string.cd_connection_status, label)
    val color = connectionColor(state)
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
