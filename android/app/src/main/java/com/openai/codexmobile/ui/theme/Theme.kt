package com.openai.codexmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SlateBlue,
    secondary = Coral,
    background = Sand,
    surface = Mist,
    onPrimary = Sand,
    onSecondary = Ink,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Mist,
    secondary = Coral,
    background = Ink,
    surface = SlateBlue,
    onPrimary = Ink,
    onSecondary = Ink,
    onBackground = Sand,
    onSurface = Sand,
)

@Composable
fun CodexMobileTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CodexTypography,
        content = content,
    )
}
