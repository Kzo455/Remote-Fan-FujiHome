package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AeroDarkColorScheme = darkColorScheme(
    primary = AeroPrimary,
    onPrimary = AeroOnPrimary,
    primaryContainer = AeroPrimaryContainer,
    onPrimaryContainer = AeroOnPrimaryContainer,
    secondary = AeroSecondary,
    onSecondary = AeroOnSecondary,
    secondaryContainer = AeroSecondaryContainer,
    onSecondaryContainer = AeroOnSecondaryContainer,
    tertiary = AeroTertiary,
    onTertiary = AeroOnTertiary,
    tertiaryContainer = AeroTertiaryContainer,
    background = AeroSurface,
    onBackground = AeroOnSurface,
    surface = AeroSurface,
    onSurface = AeroOnSurface,
    surfaceVariant = AeroSurfaceVariant,
    onSurfaceVariant = AeroOnSurfaceVariant,
    outline = AeroOutline,
    outlineVariant = AeroOutlineVariant,
    error = AeroError,
    onError = AeroOnError
)

@Composable
fun FujiHomeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AeroDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
