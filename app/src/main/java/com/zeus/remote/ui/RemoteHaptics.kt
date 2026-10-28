package com.zeus.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

val LocalRemoteHaptics = staticCompositionLocalOf { true }

@Composable
fun hapticClick(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    val enabled = LocalRemoteHaptics.current
    return {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        action()
    }
}
