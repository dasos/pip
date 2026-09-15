package com.pip.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PipLightColors = lightColorScheme(
    primary = Color(0xFF1B6BB0),
    onPrimary = Color.White,
    secondary = Color(0xFF7C4DFF),
    background = Color.White,
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
)

private val PipDarkColors = darkColorScheme(
    primary = Color(0xFF9ECBFF),
    onPrimary = Color(0xFF003256),
    secondary = Color(0xFFCDBDFF),
    onSecondary = Color(0xFF381E72),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E8),
)

@Composable
fun PipPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PipDarkColors else PipLightColors,
        content = content
    )
}