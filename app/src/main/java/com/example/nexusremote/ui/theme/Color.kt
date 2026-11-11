package com.example.nexusremote.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Deep navy base with a purple-to-violet accent.
val NexusBackground = Color(0xFF080A14)
val NexusSurface = Color(0xFF10132A)
val NexusSurfaceHigh = Color(0xFF171B36)
val NexusSurfaceHighest = Color(0xFF212645)
val NexusOutline = Color(0xFF2F3558)
val NexusOnSurface = Color(0xFFE8E9F3)
val NexusOnSurfaceMuted = Color(0xFFA2A6C0)

val NexusPurple = Color(0xFFB39DFF)
val NexusViolet = Color(0xFF7C4DFF)
val NexusPurpleDeep = Color(0xFF34286A)
val NexusOnPurple = Color(0xFF160C33)
val NexusOnPurpleDeep = Color(0xFFE6DEFF)

val NexusSuccess = Color(0xFF5BD69A)
val NexusWarning = Color(0xFFF2C14E)
val NexusError = Color(0xFFFF6B7A)
val NexusOnError = Color(0xFF3A0610)
val NexusErrorContainer = Color(0xFF4F1724)

/** Gradients shared by premium surfaces (primary buttons, OK key, connected TV card). */
object NexusGradients {
    /** Purple to violet: the app's accent. */
    val accent: Brush = Brush.linearGradient(listOf(NexusPurple, NexusViolet))

    /** Faint accent wash for highlighted cards. */
    val accentWash: Brush = Brush.linearGradient(
        listOf(NexusPurple.copy(alpha = 0.22f), NexusViolet.copy(alpha = 0.10f)),
    )

    /** Hairline border for elevated cards. */
    val cardBorder: Brush = Brush.linearGradient(
        listOf(NexusPurple.copy(alpha = 0.30f), NexusOutline.copy(alpha = 0.25f)),
    )
}
