package com.xenlon.instadownloader.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Instagram-inspired gradient colors
val InstagramPurple = Color(0xFF833AB4)
val InstagramPink = Color(0xFFE1306C)
val InstagramOrange = Color(0xFFF77737)
val InstagramYellow = Color(0xFFFCAF45)

// Dark theme colors
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E2E)
val DarkSurfaceVariant = Color(0xFF2A2A3C)
val DarkCard = Color(0xFF252538)
val AccentPurple = Color(0xFFBB86FC)
val AccentPink = Color(0xFFE91E8C)
val TextPrimary = Color(0xFFE1E1E6)
val TextSecondary = Color(0xFFA0A0B0)
val SuccessGreen = Color(0xFF4CAF50)
val ErrorRed = Color(0xFFCF6679)
val WarningOrange = Color(0xFFFF9800)

private val DarkColorScheme = darkColorScheme(
    primary = AccentPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D1F3D),
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2B1F3D),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = InstagramOrange,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF444458),
    outlineVariant = Color(0xFF333344)
)

@Composable
fun InstaDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
