package com.pip.wear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PipColorScheme = darkColorScheme(
    primary = Color(0xFF4C9AFF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFCBE0FF),
    tertiary = Color(0xFF7C4DFF),
    tertiaryContainer = Color(0xFF3A2A6E),
    onTertiaryContainer = Color(0xFFE3D6FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111318),
    onSurface = Color.White,
)

@Composable
fun PipWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PipColorScheme,
        content = content
    )
}