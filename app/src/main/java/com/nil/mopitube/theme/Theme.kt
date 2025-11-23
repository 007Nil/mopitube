package com.nil.mopitube.theme


import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color


private val DarkColors = darkColorScheme(
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFECECEC)
)


private val LightColors = lightColorScheme()


@Composable
fun MopiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}