package com.xiaoyunduo.zhiti.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF123B70)
val NavyDark = Color(0xFF0C2B52)
val Green = Color(0xFF17885B)
val Red = Color(0xFFC13B45)
val Ink = Color(0xFF172033)
val Muted = Color(0xFF637083)
val Canvas = Color(0xFFF7F9FC)
val Line = Color(0xFFDDE4EE)

private val Colors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Green,
    error = Red,
    background = Canvas,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = Line,
)

@Composable
fun ZhitiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = MaterialTheme.typography, content = content)
}

