package com.txt2epub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo500,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo700,
    secondary = Indigo700,
    onSecondary = Color.White,
    background = Color(0xFFFBFBFD),
    surface = Color.White,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceSoft,
    outline = DividerSoft,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B93FF),
    onPrimary = Color(0xFF1B1B3A),
    background = Color(0xFF101014),
    surface = Color(0xFF17171D),
    onSurface = Color(0xFFE8E8F0),
)

@Composable
fun Txt2EpubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
