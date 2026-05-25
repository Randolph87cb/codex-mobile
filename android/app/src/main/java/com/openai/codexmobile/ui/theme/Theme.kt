package com.openai.codexmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColors = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    primaryContainer = PrimaryContainer,
    secondaryContainer = SecondaryContainer,
    tertiaryContainer = TertiaryContainer,
    errorContainer = ErrorContainer,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onTertiary = OnTertiary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    onPrimaryContainer = OnPrimaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    onError = OnError,
    onErrorContainer = OnErrorContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    secondary = SecondaryContainer,
    tertiary = OnTertiaryContainer,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    primaryContainer = DarkPrimaryContainer,
    secondaryContainer = Secondary,
    tertiaryContainer = TertiaryContainer,
    errorContainer = OnErrorContainer,
    onPrimary = OnBackground,
    onSecondary = OnBackground,
    onTertiary = OnBackground,
    onBackground = Background,
    onSurface = Background,
    onSurfaceVariant = OutlineVariant,
    onPrimaryContainer = Background,
    onSecondaryContainer = Background,
    onTertiaryContainer = Background,
    onError = OnError,
    onErrorContainer = ErrorContainer,
    outline = Outline,
    outlineVariant = DarkSurfaceVariant,
    error = ErrorContainer,
)

@Composable
fun CodexMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typeScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val currentDensity = LocalDensity.current
    val resolvedTypeScale = typeScale.coerceIn(0.88f, 1.18f)
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * resolvedTypeScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CodexTypography,
            content = content,
        )
    }
}
