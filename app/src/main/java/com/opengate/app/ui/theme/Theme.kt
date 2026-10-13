package com.opengate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.opengate.app.data.settings.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Gold,
    secondary = GoldLight,
    background = Background,
    surface = Surface,
    onPrimary = Color(0xFF1A1A1A),
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C7A2E),
    secondary = Color(0xFFB9924A),
    background = Color(0xFFF5F2EA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1C1C),
    onSurface = Color(0xFF1C1C1C)
)

@Composable
fun OpenGateTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
