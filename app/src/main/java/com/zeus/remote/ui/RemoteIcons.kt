package com.zeus.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme

enum class RemoteIcon { Power, Mute, Source, Settings, Up, Down, Left, Right, Home, Back, PlayPause, Rewind, Forward, Plus, Minus, Remote, Apps, Theme }

/** Small code-native line icons, sized in a 24-unit coordinate system. */
@Composable
fun RemoteSymbol(icon: RemoteIcon, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, point(x, y), point(x2, y2), 1.65f * scale, StrokeCap.Round)
        fun path(vararg coords: Pair<Float, Float>, closed: Boolean = false) {
            val p = Path().apply {
                moveTo(coords[0].first * scale, coords[0].second * scale)
                coords.drop(1).forEach { lineTo(it.first * scale, it.second * scale) }
                if (closed) close()
            }
            drawPath(p, color, style = Stroke(1.65f * scale, cap = StrokeCap.Round))
        }
        when (icon) {
            RemoteIcon.Power -> {
                drawArc(color, -48f, 276f, false, point(4f, 4f), Size(16 * scale, 16 * scale), style = Stroke(1.65f * scale, cap = StrokeCap.Round))
                line(12f, 2f, 12f, 11f)
            }
            RemoteIcon.Up -> path(7f to 14f, 12f to 9f, 17f to 14f)
            RemoteIcon.Down -> path(7f to 10f, 12f to 15f, 17f to 10f)
            RemoteIcon.Left -> path(14f to 7f, 9f to 12f, 14f to 17f)
            RemoteIcon.Right -> path(10f to 7f, 15f to 12f, 10f to 17f)
            RemoteIcon.Plus -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
            RemoteIcon.Minus -> line(5f, 12f, 19f, 12f)
            RemoteIcon.Back -> {
                path(11f to 5f, 4f to 12f, 11f to 19f)
                line(4f, 12f, 20f, 12f)
            }
            RemoteIcon.Home -> {
                path(2f to 11f, 12f to 3f, 22f to 11f)
                path(5f to 9f, 5f to 21f, 10f to 21f, 10f to 15f, 14f to 15f, 14f to 21f, 19f to 21f, 19f to 9f)
            }
            RemoteIcon.PlayPause -> { path(3f to 5f, 12f to 12f, 3f to 19f, closed = true); line(16f, 5f, 16f, 19f); line(21f, 5f, 21f, 19f) }
            RemoteIcon.Rewind -> { path(11f to 5f, 3f to 12f, 11f to 19f, closed = true); path(21f to 5f, 13f to 12f, 21f to 19f, closed = true) }
            RemoteIcon.Forward -> { path(3f to 5f, 11f to 12f, 3f to 19f, closed = true); path(13f to 5f, 21f to 12f, 13f to 19f, closed = true) }
            RemoteIcon.Mute -> {
                path(3f to 9f, 7f to 9f, 12f to 4f, 12f to 20f, 7f to 15f, 3f to 15f, closed = true)
                line(17f, 9f, 22f, 15f); line(22f, 9f, 17f, 15f)
            }
            RemoteIcon.Source -> {
                drawRoundRect(color, point(2f, 5f), Size(20 * scale, 14 * scale), CornerRadius(3 * scale), style = Stroke(1.65f * scale))
                path(8f to 10f, 16f to 10f, 14f to 15f, 10f to 15f, closed = true)
            }
            RemoteIcon.Settings -> {
                drawCircle(color, 4 * scale, point(12f, 12f), style = Stroke(1.65f * scale))
                drawCircle(color, 8 * scale, point(12f, 12f), style = Stroke(1.65f * scale))
                line(12f, 1f, 12f, 4f); line(12f, 20f, 12f, 23f); line(1f, 12f, 4f, 12f); line(20f, 12f, 23f, 12f)
            }
            RemoteIcon.Remote -> {
                drawRoundRect(color, point(6f, 1f), Size(12 * scale, 22 * scale), CornerRadius(4 * scale), style = Stroke(1.65f * scale))
                drawCircle(color, 3 * scale, point(12f, 9f), style = Stroke(1.65f * scale)); line(10f, 16f, 14f, 16f); line(10f, 19f, 14f, 19f)
            }
            RemoteIcon.Apps -> for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) {
                drawRoundRect(color, point(x, y), Size(7 * scale, 7 * scale), CornerRadius(1.5f * scale))
            }
            RemoteIcon.Theme -> {
                drawCircle(color, 8 * scale, point(12f, 12f), style = Stroke(1.65f * scale))
                drawArc(color, -90f, 180f, true, point(4f, 4f), Size(16 * scale, 16 * scale))
            }
        }
    }
}
