package com.openai.codexmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SlateBlue,
    secondary = Coral,
    tertiary = Pine,
    background = Sand,
    surface = Color.White,
    surfaceVariant = Mist,
    primaryContainer = Mist,
    secondaryContainer = SandDeep,
    tertiaryContainer = Color(0xFFD7E4DD),
    onPrimary = Sand,
    onSecondary = Ink,
    onTertiary = Sand,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
    onPrimaryContainer = Ink,
    onSecondaryContainer = Ink,
    onTertiaryContainer = Ink,
    outline = MistDeep,
    error = ErrorRose,
)

private val DarkColors = darkColorScheme(
    primary = Mist,
    secondary = Coral,
    tertiary = Color(0xFF9EC9B2),
    background = Ink,
    surface = SlateBlueDeep,
    surfaceVariant = Color(0xFF30465F),
    primaryContainer = Color(0xFF314C67),
    secondaryContainer = Color(0xFF5E3E39),
    tertiaryContainer = Color(0xFF345044),
    onPrimary = Ink,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Sand,
    onSurface = Sand,
    onSurfaceVariant = Mist,
    onPrimaryContainer = Sand,
    onSecondaryContainer = Sand,
    onTertiaryContainer = Sand,
    outline = Color(0xFF45607D),
    error = Color(0xFFFFB3BA),
)

@Composable
fun CodexMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodexTypography,
        content = content,
    )
}
