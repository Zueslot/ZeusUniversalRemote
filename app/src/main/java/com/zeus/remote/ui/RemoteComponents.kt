package com.zeus.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.remote.data.FavoriteAppNames

@Composable
fun GlassPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    icon: RemoteIcon? = null) {
    Surface(onClick = hapticClick(onClick), enabled = enabled,
        modifier = modifier.semantics { contentDescription = label; role = Role.Button },
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = glassBorder()) {
        Box(Modifier.fillMaxSize().glassSheen(), contentAlignment = Alignment.Center) {
            val color = MaterialTheme.colorScheme.primary
            if (icon != null) RemoteSymbol(icon, Modifier.size(24.dp).drawBehind {
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .24f), Color.Transparent),
                    center = center, radius = 20.dp.toPx()), radius = 20.dp.toPx())
            }, color)
            else Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = color)
        }
    }
}

@Composable
fun RoundControl(
    label: String, icon: RemoteIcon, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, diameter: Dp = 56.dp, caption: String? = null, tint: Color? = null
) {
    Surface(onClick = hapticClick(onClick), enabled = enabled,
        modifier = modifier.size(diameter).semantics { contentDescription = label; role = Role.Button },
        shape = CircleShape, color = MaterialTheme.colorScheme.surface,
        border = glassBorder(), shadowElevation = 0.dp) {
        Column(Modifier.fillMaxSize().glassSheen(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            RemoteSymbol(icon, Modifier.size(if (caption == null) 24.dp else 20.dp),
                (tint ?: MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else .38f))
            if (caption != null) Text(caption, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DirectionPad(enabled: Boolean, onDirection: (RemoteIcon) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.size(200.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface,
        border = glassBorder(), shadowElevation = 0.dp) {
        Box(Modifier.fillMaxSize().glassSheen()) {
            listOf(RemoteIcon.Up to Alignment.TopCenter, RemoteIcon.Down to Alignment.BottomCenter,
                RemoteIcon.Left to Alignment.CenterStart, RemoteIcon.Right to Alignment.CenterEnd).forEach { (icon, alignment) ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val currentDirection by rememberUpdatedState(onDirection)
                var repeated by remember { mutableStateOf(false) }
                LaunchedEffect(pressed, enabled) {
                    if (pressed && enabled) {
                        repeated = false
                        delay(400)
                        // Include the initial step when the hold begins; suppress the release click.
                        repeated = true
                        currentDirection(icon)
                        while (isActive) {
                            delay(100)
                            currentDirection(icon)
                        }
                    }
                }
                IconButton(onClick = hapticClick {
                    if (!repeated) onDirection(icon)
                    repeated = false
                }, enabled = enabled, interactionSource = interaction,
                    modifier = Modifier.align(alignment).padding(6.dp).size(48.dp)
                        .semantics { contentDescription = "Navigate ${icon.name.lowercase()}" }) {
                    RemoteSymbol(icon, Modifier.size(26.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .35f))
                }
            }
            Surface(onClick = hapticClick { onDirection(RemoteIcon.Remote) }, enabled = enabled,
                modifier = Modifier.align(Alignment.Center).size(82.dp).semantics { contentDescription = "OK / Select"; role = Role.Button },
                shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                border = glassBorder(), shadowElevation = 0.dp) {
                Box(Modifier.fillMaxSize().glassSheen(), contentAlignment = Alignment.Center) {
                    Text("OK", fontSize = 23.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
                }
            }
        }
    }
}

@Composable
fun ControlRail(label: String, top: RemoteIcon, bottom: RemoteIcon, enabled: Boolean, onTop: () -> Unit, onBottom: () -> Unit) {
    Surface(shape = RoundedCornerShape(34.dp), color = MaterialTheme.colorScheme.surface,
        border = glassBorder(), shadowElevation = 0.dp) {
        Column(Modifier.width(62.dp).height(128.dp).glassSheen(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = hapticClick(onTop), enabled = enabled, modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "$label ${top.name}" }) {
                RemoteSymbol(top, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .35f))
            }
            Text(label, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = hapticClick(onBottom), enabled = enabled, modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "$label ${bottom.name}" }) {
                RemoteSymbol(bottom, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .35f))
            }
        }
    }
}

@Composable
fun ConnectionDot(connected: Boolean) {
    Box(Modifier.size(6.dp).background(if (connected) Color(0xFF57C88B) else MaterialTheme.colorScheme.outline, CircleShape))
}

@Composable
fun RemoteBottomBar(selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Surface(modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp).widthIn(max = 420.dp).fillMaxWidth(),
        shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surface,
        border = glassBorder(), shadowElevation = 0.dp) {
        Row(Modifier.glassSheen().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Remote" to RemoteIcon.Remote, "Apps" to RemoteIcon.Apps, "Settings" to RemoteIcon.Settings).forEach { (name, icon) ->
                val active = selected == name
                Surface(onClick = hapticClick { onSelect(name) }, enabled = enabled, modifier = Modifier.weight(1f)
                    .semantics { this.selected = active; role = Role.Tab }, shape = RoundedCornerShape(28.dp),
                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent) {
                    Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        RemoteSymbol(icon, Modifier.size(21.dp), if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(name, fontSize = 10.sp, lineHeight = 14.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteAppTiles(large: Boolean = false, onAppClick: (String) -> Unit = {}, available: (String) -> Boolean = { false },
    displayName: (String) -> String = { it }) {
    val colors = listOf(Color(0xFFED565F), Color(0xFF9B8AFB), Color(0xFFE3AB51), Color(0xFF56BFAE))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val columns = if (large) 2 else 4
        FavoriteAppNames.withIndex().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (large) 10.dp else 6.dp)) {
                row.forEach { (index, name) ->
                    val appName = displayName(name)
                    Surface(onClick = hapticClick { onAppClick(name) }, enabled = available(name), modifier = Modifier.weight(1f)
                        .semantics { contentDescription = if (available(name)) "Launch $appName" else "$appName, connect companion or select its installed app in Apps" },
                        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                        border = glassBorder()) {
                        Column(Modifier.height(if (large) 112.dp else 72.dp).glassSheen().padding(if (large) 8.dp else 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (appName.startsWith("SmartTube", ignoreCase = true)) "▶" else appName.take(1), fontSize = if (large) 25.sp else 17.sp, lineHeight = if (large) 30.sp else 22.sp,
                                color = colors[index], fontWeight = FontWeight.Bold)
                            Text(appName, fontSize = if (large) 11.sp else 10.sp, lineHeight = 13.sp, maxLines = 2, textAlign = TextAlign.Center,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
