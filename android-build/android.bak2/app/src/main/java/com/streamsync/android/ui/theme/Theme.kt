package com.streamsync.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand colors
val SyncBlue = Color(0xFF4A90D9)
val SyncBlueDark = Color(0xFF2C6FB8)
val SyncBlueLight = Color(0xFF7AB3E8)
val SyncGreen = Color(0xFF34C759)
val SyncOrange = Color(0xFFFF9500)
val SyncRed = Color(0xFFFF3B30)
val SyncPurple = Color(0xFFAF52DE)

// Dark theme
val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkSurfaceVariant = Color(0xFF21262D)
val DarkOnBackground = Color(0xFFE6EDF3)
val DarkOnSurface = Color(0xFFC9D1D9)

// Light theme
val LightBackground = Color(0xFFF6F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEBEDEF)
val LightOnBackground = Color(0xFF1C2128)
val LightOnSurface = Color(0xFF24292F)

private val DarkColorScheme = darkColorScheme(
    primary = SyncBlue,
    onPrimary = Color.White,
    primaryContainer = SyncBlueDark,
    secondary = SyncGreen,
    tertiary = SyncPurple,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    error = SyncRed,
    outline = Color(0xFF30363D),
)

private val LightColorScheme = lightColorScheme(
    primary = SyncBlue,
    onPrimary = Color.White,
    primaryContainer = SyncBlueLight,
    secondary = SyncGreen,
    tertiary = SyncPurple,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    error = SyncRed,
    outline = Color(0xFFD0D7DE),
)

@Composable
fun StreamSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
