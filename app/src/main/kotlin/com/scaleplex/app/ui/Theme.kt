package com.scaleplex.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColors(
    primary = Color(0xFF1E6FD9),
    primaryVariant = Color(0xFF1557B0),
    secondary = Color(0xFF00A884),
    background = Color(0xFFF5F6F8),
    surface = Color.White,
)

private val DarkColors = darkColors(
    primary = Color(0xFF4C8DFF),
    primaryVariant = Color(0xFF2F6FE0),
    secondary = Color(0xFF00C49A),
    background = Color(0xFF1E1F22),
    surface = Color(0xFF26282C),
)

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colors = if (dark) DarkColors else LightColors,
        content = content,
    )
}
