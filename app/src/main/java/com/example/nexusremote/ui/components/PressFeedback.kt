package com.example.nexusremote.ui.components

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/** Returns a lambda that gives haptic and/or click-sound feedback according to settings. */
@Composable
fun rememberPressFeedback(hapticsEnabled: Boolean, soundEnabled: Boolean): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(hapticsEnabled, soundEnabled, haptics, view) {
        {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            // Plays only when the user has "touch sounds" enabled in system settings.
            if (soundEnabled) view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }
}
