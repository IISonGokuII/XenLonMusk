package com.xenlon.instadownloader.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Instagram-inspired gradient colors
val InstagramPurple = Color(0xFF833AB4)
val InstagramPink = Color(0xFFE1306C)
val InstagramOrange = Color(0xFFF77737)
val InstagramYellow = Color(0xFFFCAF45)

// Dark theme colors (normal)
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

// AMOLED theme colors
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A14)
val AmoledSurfaceVariant = Color(0xFF151520)
val AmoledCard = Color(0xFF101018)

val LocalIsAmoled = compositionLocalOf { false }

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

private val AmoledColorScheme = darkColorScheme(
    primary = AccentPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1028),
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A1028),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = InstagramOrange,
    onTertiary = Color.White,
    background = AmoledBackground,
    onBackground = TextPrimary,
    surface = AmoledSurface,
    onSurface = TextPrimary,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF333340),
    outlineVariant = Color(0xFF222230)
)

@Composable
fun InstaDownloaderTheme(isAmoled: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (isAmoled) AmoledColorScheme else DarkColorScheme
    androidx.compose.runtime.CompositionLocalProvider(LocalIsAmoled provides isAmoled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
