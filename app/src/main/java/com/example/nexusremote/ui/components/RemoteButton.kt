package com.example.nexusremote.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Delay before auto-repeat starts, and the throttled repeat interval while held. */
const val REPEAT_INITIAL_DELAY_MS = 400L
const val REPEAT_INTERVAL_MS = 150L

/**
 * Hard cap on auto-repeats for one continuous hold (about 15 s at 150 ms). A stuck finger or
 * a stuck touch event therefore stops on its own; the user simply presses again to continue.
 */
const val MAX_REPEATS_PER_HOLD = 100

/**
 * Press area for remote buttons.
 * - Tap: [onPress] once, then [onRelease].
 * - Hold (when [repeatable]): [onPress], then [onRepeat] every [REPEAT_INTERVAL_MS] after an
 *   initial delay (at most [MAX_REPEATS_PER_HOLD] times), then [onRelease] when the finger
 *   lifts, the gesture is cancelled, or the button leaves the screen.
 * - [fireOnTapOnly]: the action fires on finger UP, and only for a completed tap. A gesture
 *   that turns into a scroll sends nothing. Used for POWER.
 * Accessibility: exposed as a Button with a click action, so TalkBack double-tap works.
 */
@Composable
fun PressArea(
    contentDescription: String,
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    repeatable: Boolean = false,
    onRepeat: () -> Unit = {},
    onRelease: () -> Unit = {},
    fireOnTapOnly: Boolean = false,
    shape: Shape,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    val press by rememberUpdatedState(onPress)
    val repeat by rememberUpdatedState(onRepeat)
    val release by rememberUpdatedState(onRelease)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "remoteButtonScale",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(if (pressed) containerColor.copy(alpha = 0.7f) else containerColor)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (!enabled) disabled()
                onClick {
                    if (enabled) {
                        press()
                        release()
                    }
                    enabled
                }
            }
            .pointerInput(enabled, repeatable, fireOnTapOnly) {
                if (!enabled) return@pointerInput
                if (fireOnTapOnly) {
                    // Visual feedback on down, but the action only on a completed tap.
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                            }
                        },
                        onTap = {
                            press()
                            release()
                        },
                    )
                    return@pointerInput
                }
                coroutineScope {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            press()
                            val repeatJob = if (repeatable) {
                                // Cancelled when the finger lifts or this button leaves the
                                // composition (screen left), via the finally block below.
                                launch {
                                    delay(REPEAT_INITIAL_DELAY_MS)
                                    for (i in 0 until MAX_REPEATS_PER_HOLD) {
                                        repeat()
                                        delay(REPEAT_INTERVAL_MS)
                                    }
                                }
                            } else {
                                null
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                repeatJob?.cancel()
                                pressed = false
                                release()
                            }
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Round icon button for the remote. Minimum size is 48dp; most remote buttons use 64dp. */
@Composable
fun RemoteIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    repeatable: Boolean = false,
    onRepeat: () -> Unit = {},
    onRelease: () -> Unit = {},
    fireOnTapOnly: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    PressArea(
        contentDescription = contentDescription,
        enabled = enabled,
        onPress = onPress,
        repeatable = repeatable,
        onRepeat = onRepeat,
        onRelease = onRelease,
        fireOnTapOnly = fireOnTapOnly,
        shape = shape,
        containerColor = containerColor,
        modifier = modifier.size(size),
    ) {
        // The icon itself is decorative; the button carries the description.
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.42f))
    }
}
