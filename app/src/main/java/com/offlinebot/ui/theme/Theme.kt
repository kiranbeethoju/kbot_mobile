package com.offlinebot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F5D50),
    secondary = Color(0xFF755C2B),
    tertiary = Color(0xFF4758A8),
    background = Color(0xFFF8F7F3),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1E1F1C),
    onSurface = Color(0xFF1E1F1C)
)

@Composable
fun OfflineBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        content = content
    )
}
