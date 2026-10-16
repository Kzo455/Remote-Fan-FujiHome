package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AeroDarkColorScheme = darkColorScheme(
    primary = AeroPrimary,
    onPrimary = AeroOnPrimary,
    primaryContainer = AeroPrimaryContainer,
    onPrimaryContainer = AeroOnPrimaryContainer,
    inversePrimary = AeroInversePrimary,
    secondary = AeroSecondary,
    onSecondary = AeroOnSecondary,
    secondaryContainer = AeroSecondaryContainer,
    onSecondaryContainer = AeroOnSecondaryContainer,
    tertiary = AeroTertiary,
    onTertiary = AeroOnTertiary,
    tertiaryContainer = AeroTertiaryContainer,
    onTertiaryContainer = AeroOnTertiaryContainer,
    background = AeroBackground,
    onBackground = AeroOnBackground,
    surface = AeroSurface,
    onSurface = AeroOnSurface,
    surfaceVariant = AeroSurfaceVariant,
    onSurfaceVariant = AeroOnSurfaceVariant,
    inverseSurface = AeroInverseSurface,
    inverseOnSurface = AeroInverseOnSurface,
    surfaceTint = AeroSurfaceTint,
    outline = AeroOutline,
    outlineVariant = AeroOutlineVariant,
    error = AeroError,
    onError = AeroOnError,
    errorContainer = AeroErrorContainer,
    onErrorContainer = AeroOnErrorContainer
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
