package com.zeus.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Static soft light beneath translucent controls, with no per-frame blur cost. */
@Composable
fun Modifier.glassBackdrop(): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    return drawBehind {
        drawRect(colors.background)
        drawRect(Brush.radialGradient(listOf(Color(0xFF2A7C9B).copy(alpha = if (dark) .42f else .16f), Color.Transparent),
            center = Offset(size.width * .04f, size.height * .23f), radius = size.height * .62f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF7862BC).copy(alpha = if (dark) .30f else .18f), Color.Transparent),
            center = Offset(size.width * 1.05f, size.height * .65f), radius = size.height * .57f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF4DBDAF).copy(alpha = if (dark) .17f else .14f), Color.Transparent),
            center = Offset(size.width * .2f, size.height * 1.04f), radius = size.height * .45f))
    }
}

@Composable
fun glassBorder(): BorderStroke {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return BorderStroke(1.dp, Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (dark) .48f else .95f),
        Color.White.copy(alpha = if (dark) .08f else .24f),
        MaterialTheme.colorScheme.primary.copy(alpha = .25f))))
}

/** Applied inside the Surface so highlights follow its rounded clipping. */
@Composable
fun Modifier.glassSheen(): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return drawBehind {
        drawRect(Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (dark) .13f else .52f),
            Color.White.copy(alpha = .02f),
            Color.White.copy(alpha = if (dark) .04f else .18f)),
            start = Offset.Zero, end = Offset(size.width * .8f, size.height)))
    }
}
