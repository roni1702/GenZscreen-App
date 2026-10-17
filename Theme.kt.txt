package com.example.genzscreen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Digital Wellbeing Inspired Colors
val BackgroundLight = Color(0xFFF8F9FA)
val SurfaceLight = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1F1F1F)
val TextSecondary = Color(0xFF5F6368)
val BorderLight = Color(0xFFEEEEEE)

val FacebookColor = Color(0xFF1877F2)
val YouTubeColor = Color(0xFFFF0000)
val InstagramColor = Color(0xFFE1306C)
val TikTokColor = Color(0xFF000000)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8), // Google Blue
    onPrimary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = BorderLight,
    onSurfaceVariant = TextSecondary
)

@Composable
fun GenZscreenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography, // Default Material 3 Typography
        content = content
    )
}
