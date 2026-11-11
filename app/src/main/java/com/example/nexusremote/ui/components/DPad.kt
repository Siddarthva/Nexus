package com.example.nexusremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.example.nexusremote.ui.theme.NexusGradients
import com.example.nexusremote.ui.theme.NexusOnPurple
import com.example.nexusremote.ui.theme.NexusViolet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.example.nexusremote.R
import com.example.nexusremote.domain.command.TvCommand

/**
 * Large circular D-pad with OK in the centre. Arrow keys auto-repeat while held.
 * Scales to the available width (max 300dp) and keeps every target well above 48dp.
 */
@Composable
fun DPad(
    enabled: Boolean,
    onPress: (TvCommand) -> Unit,
    onRepeat: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth * 0.82f, 300.dp)
        val arrowSize = diameter * 0.30f
        val okSize = diameter * 0.36f
        val ring = Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh),
        )

        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                .background(ring, CircleShape)
                .border(1.dp, NexusGradients.cardBorder, CircleShape),
        ) {
            // Soft glow around OK (decorative: no touch handling, no semantics).
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(okSize * 1.9f)
                    .background(
                        Brush.radialGradient(listOf(NexusViolet.copy(alpha = 0.45f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Arrow(TvCommand.UP, R.string.cd_dpad_up, Alignment.TopCenter, arrowSize, enabled, onPress, onRepeat, onRelease)
            Arrow(TvCommand.DOWN, R.string.cd_dpad_down, Alignment.BottomCenter, arrowSize, enabled, onPress, onRepeat, onRelease)
            Arrow(TvCommand.LEFT, R.string.cd_dpad_left, Alignment.CenterStart, arrowSize, enabled, onPress, onRepeat, onRelease)
            Arrow(TvCommand.RIGHT, R.string.cd_dpad_right, Alignment.CenterEnd, arrowSize, enabled, onPress, onRepeat, onRelease)

            PressArea(
                contentDescription = stringResource(R.string.cd_ok),
                enabled = enabled,
                onPress = { onPress(TvCommand.SELECT) },
                onRelease = onRelease,
                shape = CircleShape,
                containerColor = Color.Transparent,
                modifier = Modifier.align(Alignment.Center).size(okSize),
            ) {
                // Gradient OK key (PressArea already clips to the circle).
                Box(
                    Modifier.fillMaxSize().background(NexusGradients.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.remote_ok),
                        style = MaterialTheme.typography.titleLarge,
                        color = NexusOnPurple,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Arrow(
    command: TvCommand,
    descriptionRes: Int,
    alignment: Alignment,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onPress: (TvCommand) -> Unit,
    onRepeat: (TvCommand) -> Unit,
    onRelease: () -> Unit,
) {
    val icon = when (command) {
        TvCommand.UP -> Icons.Rounded.KeyboardArrowUp
        TvCommand.DOWN -> Icons.Rounded.KeyboardArrowDown
        TvCommand.LEFT -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
        else -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
    }
    RemoteIconButton(
        icon = icon,
        contentDescription = stringResource(descriptionRes),
        enabled = enabled,
        onPress = { onPress(command) },
        repeatable = true,
        onRepeat = { onRepeat(command) },
        onRelease = onRelease,
        size = size,
        containerColor = Color.Transparent,
        modifier = Modifier.align(alignment).padding(6.dp),
    )
}
