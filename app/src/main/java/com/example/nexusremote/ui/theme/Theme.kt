package com.example.nexusremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexusColorScheme = darkColorScheme(
    primary = NexusPurple,
    onPrimary = NexusOnPurple,
    primaryContainer = NexusPurpleDeep,
    onPrimaryContainer = NexusOnPurpleDeep,
    secondary = NexusOnSurfaceMuted,
    onSecondary = NexusBackground,
    secondaryContainer = NexusSurfaceHighest,
    onSecondaryContainer = NexusOnSurface,
    background = NexusBackground,
    onBackground = NexusOnSurface,
    surface = NexusSurface,
    onSurface = NexusOnSurface,
    surfaceVariant = NexusSurfaceHigh,
    onSurfaceVariant = NexusOnSurfaceMuted,
    surfaceContainerLowest = NexusBackground,
    surfaceContainerLow = NexusSurface,
    surfaceContainer = NexusSurface,
    surfaceContainerHigh = NexusSurfaceHigh,
    surfaceContainerHighest = NexusSurfaceHighest,
    outline = NexusOutline,
    outlineVariant = NexusSurfaceHighest,
    error = NexusError,
    onError = NexusOnError,
    errorContainer = NexusErrorContainer,
    onErrorContainer = NexusOnSurface,
)

/** NEXUS is always dark, by design. */
@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusColorScheme,
        typography = NexusTypography,
        content = content,
    )
}
