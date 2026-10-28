package com.zeus.remote.ui

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Night = darkColorScheme(
    primary = Color(0xFF7CB8FF), onPrimary = Color(0xFF071A2D),
    background = Color(0xFF09131F), onBackground = Color(0xFFF4F5F7),
    surface = Color(0x80304760), onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Color(0xFF283E53), onSurfaceVariant = Color(0xFFC1D0DF),
    outlineVariant = Color(0xFF343A43), outline = Color(0xFF77818E),
    error = Color(0xFFFF657E)
)
private val Day = lightColorScheme(
    primary = Color(0xFF2465B6), onPrimary = Color.White,
    background = Color(0xFFE7EFF5), onBackground = Color(0xFF1C222B),
    surface = Color(0x99FFFFFF), onSurface = Color(0xFF1C222B),
    surfaceVariant = Color(0xFFE2E6ED), onSurfaceVariant = Color(0xFF566170),
    outlineVariant = Color(0xFFD5DAE3), outline = Color(0xFF798493),
    error = Color(0xFFCB2148)
)

@Composable
fun RemoteTheme(dark: Boolean, haptics: Boolean = true, content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalRemoteHaptics provides haptics) {
        MaterialTheme(colorScheme = if (dark) Night else Day, content = content)
    }
}
