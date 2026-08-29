package com.pip.phone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PipColors = lightColorScheme(
    primary = Color(0xFF1B6BB0),
    onPrimary = Color.White,
    secondary = Color(0xFF7C4DFF),
    background = Color.White,
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
)

@Composable
fun PipPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PipColors,
        content = content
    )
}