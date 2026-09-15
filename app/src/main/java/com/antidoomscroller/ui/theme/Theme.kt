package com.antidoomscroller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C4FF),
    onPrimary = Color(0xFF15182B),
    secondary = Color(0xFF9AA3B2),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFECEEF5),
    surface = Color(0xFF16181F),
    onSurface = Color(0xFFECEEF5),
    surfaceVariant = Color(0xFF1F222B),
    onSurfaceVariant = Color(0xFFB6BDCC),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D4DA8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A6273),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF454B58),
)

@Composable
fun AntiDoomScrollerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
