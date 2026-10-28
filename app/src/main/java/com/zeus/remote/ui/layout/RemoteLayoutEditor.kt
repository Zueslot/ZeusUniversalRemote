package com.zeus.remote.ui.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import com.zeus.remote.data.*
import com.zeus.remote.ui.glassBorder
import com.zeus.remote.ui.glassSheen
import com.zeus.remote.ui.GlassPill
import kotlin.math.roundToInt

private val LayoutSaver = Saver<RemoteLayout, String>(save = { it.encode() }, restore = { RemoteLayout.decode(it) })

@Composable
fun RemoteLayoutEditor(
    savedJson: String, onSave: (String) -> Unit, onEditingChanged: (Boolean) -> Unit,
    connectionStatus: String, onReconnectDevices: () -> Unit,
    onOpenApps: () -> Unit, onOpenSettings: () -> Unit, onToggleTheme: () -> Unit,
    displayLabel: (RemoteControl) -> String = { it.label },
    content: @Composable (RemoteControl, Boolean) -> Unit
) {
    val saved = remember(savedJson) { RemoteLayout.decode(savedJson) }
    var draft by rememberSaveable(stateSaver = LayoutSaver) { mutableStateOf(saved) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedName by rememberSaveable { mutableStateOf(RemoteControl.POWER.name) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var menuX by rememberSaveable { mutableStateOf(.05f) }
    var menuY by rememberSaveable { mutableStateOf(.67f) }
    val selected = ActiveRemoteControls.find { it.name == selectedName } ?: RemoteControl.POWER
    SideEffect { onEditingChanged(editing) }
    DisposableEffect(Unit) { onDispose { onEditingChanged(false) } }
    BackHandler(editing) { draft = saved; editing = false; showTools = false }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val density = LocalDensity.current
        val shown = if (editing) draft else saved
        ActiveRemoteControls.forEach { control ->
            PositionedControl(control, shown[control], width, height, editing, selected == control,
                onSelect = { selectedName = control.name }, onAdjust = { showTools = true },
                onMove = { draft = draft.update(control, it) }) { content(control, editing) }
        }
        if (editing) Text("Drag to move · tap to resize · Menu to save", modifier = Modifier.align(Alignment.BottomCenter)
            .padding(bottom = 4.dp).background(MaterialTheme.colorScheme.background.copy(alpha = .85f), RoundedCornerShape(12.dp)).padding(8.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

        // This handle moves independently so no permanently reserved strip limits the canvas.
        Box(Modifier.offset {
            IntOffset(with(density) { ((width - 76f).coerceAtLeast(0f) * menuX).dp.roundToPx() },
                with(density) { ((height - 48f).coerceAtLeast(0f) * menuY).dp.roundToPx() })
            }.zIndex(5f).pointerInput(width, height, density.density) {
                detectDragGestures { change, delta ->
                    change.consume()
                    menuX = (menuX + delta.x / density.density / (width - 76f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    menuY = (menuY + delta.y / density.density / (height - 48f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
            }) {
            GlassPill(if (editing) "Editing" else "Menu", { menuExpanded = true },
                modifier = Modifier.size(76.dp, 48.dp).semantics { contentDescription = "Remote menu. Drag to move menu." })
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                Text(connectionStatus, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                if (editing) {
                    DropdownMenuItem(text = { Text("Save layout") }, onClick = { onSave(draft.encode()); editing = false; menuExpanded = false })
                    DropdownMenuItem(text = { Text("Resize / position controls") }, onClick = { showTools = true; menuExpanded = false })
                    DropdownMenuItem(text = { Text("Reset to default") }, onClick = { draft = RemoteLayout.Default; menuExpanded = false })
                    DropdownMenuItem(text = { Text("Cancel changes") }, onClick = { draft = saved; editing = false; menuExpanded = false })
                } else {
                    DropdownMenuItem(text = { Text("Reconnect devices") }, onClick = { menuExpanded = false; onReconnectDevices() })
                    DropdownMenuItem(text = { Text("Edit layout") }, onClick = { draft = saved; editing = true; menuExpanded = false })
                    DropdownMenuItem(text = { Text("Apps") }, onClick = { menuExpanded = false; onOpenApps() })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false; onOpenSettings() })
                    DropdownMenuItem(text = { Text("Switch light / dark theme") }, onClick = { menuExpanded = false; onToggleTheme() })
                }
                Text("Drag Menu if it covers a control.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (editing && showTools) {
            AlertDialog(onDismissRequest = { showTools = false }, title = { Text("Resize / position") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActiveRemoteControls.forEach { control ->
                            FilterChip(selected = selected == control, onClick = { selectedName = control.name }, label = { Text(displayLabel(control)) })
                        }
                    }
                    val placement = draft[selected]
                    val range = RemoteLayoutGeometry.scaleRange(selected, width, height)
                    val scale = placement.scale.coerceIn(range.start, range.endInclusive)
                    Text(displayLabel(selected), style = MaterialTheme.typography.titleMedium)
                    Text("Size: ${(scale * 100).roundToInt()}%")
                    Slider(value = scale, onValueChange = { draft = draft.update(selected, placement.copy(scale = it)) },
                        valueRange = range, modifier = Modifier.semantics { contentDescription = "${selected.label} size" })
                    Text("Horizontal position")
                    Slider(value = placement.x, onValueChange = { draft = draft.update(selected, placement.copy(x = it)) },
                        modifier = Modifier.semantics { contentDescription = "${selected.label} horizontal position" })
                    Text("Vertical position")
                    Slider(value = placement.y, onValueChange = { draft = draft.update(selected, placement.copy(y = it)) },
                        modifier = Modifier.semantics { contentDescription = "${selected.label} vertical position" })
                    Text("0% and 100% reach the edges of the usable screen. System status and gesture bars remain protected.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton(onClick = { showTools = false }) { Text("Done") } })
        }
    }
}
@Composable
private fun PositionedControl(
    control: RemoteControl, placement: ControlPlacement, canvasWidth: Float, canvasHeight: Float, editing: Boolean, selected: Boolean,
    onSelect: () -> Unit, onAdjust: () -> Unit, onMove: (ControlPlacement) -> Unit, content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val frame = RemoteLayoutGeometry.frame(control, placement, canvasWidth, canvasHeight)
    val base = RemoteLayoutGeometry.baseSize(control, canvasWidth)
    val currentPlacement by rememberUpdatedState(placement)
    val currentMove by rememberUpdatedState(onMove)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentAdjust by rememberUpdatedState(onAdjust)
    Box(Modifier.offset { IntOffset(with(density) { frame.left.dp.roundToPx() }, with(density) { frame.top.dp.roundToPx() }) }
        .size(frame.width.dp, frame.height.dp).zIndex(if (editing && selected) 1f else 0f)) {
        // Measure at the original size, then transform both rendering and pointer coordinates.
        // The containing layout has the actual resized bounds, rather than reserving the old size.
        Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val baseWidth = base.width.dp.roundToPx().coerceAtLeast(1)
            val baseHeight = base.height.dp.roundToPx().coerceAtLeast(1)
            val child = measurables.single().measure(Constraints.fixed(baseWidth, baseHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                child.placeWithLayer(0, 0) {
                    scaleX = constraints.maxWidth.toFloat() / baseWidth
                    scaleY = constraints.maxHeight.toFloat() / baseHeight
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
        if (editing) {
            Box(Modifier.matchParentSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .06f), RoundedCornerShape(10.dp))
                .border(BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(10.dp))
                .semantics { contentDescription = "Move ${control.label}" }
                .pointerInput(control, canvasWidth, canvasHeight, density.density) {
                    var drag = currentPlacement
                    detectDragGestures(
                        onDragStart = { drag = currentPlacement; currentSelect() },
                        onDrag = { change, delta ->
                            change.consume()
                            drag = RemoteLayoutGeometry.move(control, drag, delta.x / density.density,
                                delta.y / density.density, canvasWidth, canvasHeight)
                            currentMove(drag)
                        })
                }.clickable { currentSelect(); currentAdjust() }) {
                if (selected) Text(control.label, modifier = Modifier.background(MaterialTheme.colorScheme.primary).padding(3.dp),
                    color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
    }
}
