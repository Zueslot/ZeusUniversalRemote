package com.zeus.remote.data

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

enum class RemoteControl(val label: String) {
    POWER("Power (both devices)"), SOURCE("Source"), DPAD("D-pad"), VOLUME("Volume"), HOME("Home"),
    PLAY_PAUSE("Play / Pause"), BACK("Back"), MEDIA("Rewind / Forward"),
    SMARTTUBE("SmartTube"), NUVIO("Nuvio"), STREAMIO("Streamio"), PLAYFY("PLAYFy TV"), REEZEN("Reezen"),
    BRANDING("ZEUS / tagline")
}

// Retain retired IDs in saved layouts for compatibility, but never render or offer them in the editor.
val ActiveRemoteControls = RemoteControl.entries.filterNot {
    it == RemoteControl.SOURCE || it == RemoteControl.PLAY_PAUSE || it == RemoteControl.MEDIA || it == RemoteControl.VOLUME || it == RemoteControl.STREAMIO
}

/** Positions are fractions of remaining canvas space, so controls stay in bounds after resizing. */
data class ControlPlacement(val x: Float, val y: Float, val scale: Float = 1f) {
    fun sanitized(fallback: ControlPlacement) = ControlPlacement(
        if (x.isFinite()) x.coerceIn(0f, 1f) else fallback.x,
        if (y.isFinite()) y.coerceIn(0f, 1f) else fallback.y,
        if (scale.isFinite()) scale.coerceIn(.6f, 1.6f) else fallback.scale
    )
}

data class ControlFrame(val left: Float, val top: Float, val width: Float, val height: Float)
data class ControlSize(val width: Float, val height: Float)

data class RemoteLayout(val controls: Map<RemoteControl, ControlPlacement>) {
    operator fun get(control: RemoteControl): ControlPlacement = controls[control] ?: Default.controls.getValue(control)
    fun update(control: RemoteControl, placement: ControlPlacement) = copy(controls = controls +
        (control to placement.sanitized(Default[control])))

    fun encode(): String = JSONObject().put("version", 6).put("controls", JSONObject().apply {
        RemoteControl.entries.forEach { control ->
            val placement = get(control).sanitized(Default[control])
            put(control.name, JSONObject().put("x", placement.x.toDouble()).put("y", placement.y.toDouble()).put("scale", placement.scale.toDouble()))
        }
    }).toString()

    companion object {
        val Default = RemoteLayout(mapOf(
            RemoteControl.POWER to ControlPlacement(1f, 0f),
            RemoteControl.SOURCE to ControlPlacement(1f, .15f),
            RemoteControl.DPAD to ControlPlacement(.5f, .30f, 1.4f),
            RemoteControl.VOLUME to ControlPlacement(0f, .65f),
            RemoteControl.HOME to ControlPlacement(.5f, .67f),
            RemoteControl.PLAY_PAUSE to ControlPlacement(.65f, .60f),
            RemoteControl.BACK to ControlPlacement(.05f, .67f),
            RemoteControl.MEDIA to ControlPlacement(1f, .65f),
            RemoteControl.SMARTTUBE to ControlPlacement(.05f, .90f),
            RemoteControl.NUVIO to ControlPlacement(.35f, .90f),
            RemoteControl.STREAMIO to ControlPlacement(.5f, 1f),
            RemoteControl.PLAYFY to ControlPlacement(.65f, .90f),
            RemoteControl.REEZEN to ControlPlacement(.95f, .90f),
            RemoteControl.BRANDING to ControlPlacement(0f, 0f)
        ))

        fun decode(value: String): RemoteLayout = try {
            val json = JSONObject(value)
            val version = json.optInt("version")
            // Apply the requested aligned four-app layout once; subsequent edits remain editable.
            if (version !in 1..6 || version == 2) Default else {
                val entries = json.optJSONObject("controls") ?: JSONObject()
                var decoded = RemoteLayout(RemoteControl.entries.associateWith { control ->
                    val fallback = Default[control]
                    entries.optJSONObject(control.name)?.let { item ->
                        ControlPlacement(item.optDouble("x", fallback.x.toDouble()).toFloat(),
                            item.optDouble("y", fallback.y.toDouble()).toFloat(),
                            item.optDouble("scale", 1.0).toFloat()).sanitized(fallback)
                    } ?: fallback
                })
                // Apply the requested Back/Settings swap once; later layout edits are preserved.
                if (version < 6) decoded = decoded.update(RemoteControl.BACK, decoded[RemoteControl.BACK].copy(x = .05f))
                // Make room for the restored top-left title without resetting the user's other controls.
                val power = decoded[RemoteControl.POWER]
                if (version == 1 && power.x < .7f && power.y < .1f) {
                    decoded.update(RemoteControl.POWER, power.copy(x = 1f))
                } else decoded
            }
        } catch (_: Exception) { Default }
    }
}

object RemoteLayoutGeometry {
    const val CanvasHeight = 520f
    fun baseSize(control: RemoteControl, canvasWidth: Float): ControlSize = when (control) {
        RemoteControl.BRANDING -> ControlSize(200f, 56f)
        RemoteControl.DPAD -> ControlSize(200f, 200f)
        RemoteControl.VOLUME, RemoteControl.MEDIA -> ControlSize(62f, 128f)
        RemoteControl.BACK, RemoteControl.HOME -> ControlSize(76f, 48f)
        RemoteControl.SOURCE -> ControlSize(52f, 52f)
        RemoteControl.POWER, RemoteControl.PLAY_PAUSE -> ControlSize(56f, 56f)
        else -> ControlSize(76f.coerceAtMost((canvasWidth - 24f) / 4f).coerceAtLeast(48f), 72f)
    }

    fun scaleRange(control: RemoteControl, canvasWidth: Float, canvasHeight: Float = CanvasHeight): ClosedFloatingPointRange<Float> {
        val size = baseSize(control, canvasWidth)
        val maximum = min(1.6f, min(canvasWidth / size.width, canvasHeight / size.height))
        // D-pad and rails contain multiple 48dp buttons; shrinking the entire group
        // below its native size would make those individual touch targets too small.
        val groupMinimum = if (control in listOf(RemoteControl.DPAD, RemoteControl.VOLUME, RemoteControl.MEDIA)) 1f else .6f
        val minimum = min(maximum, max(groupMinimum, max(48f / size.width, 48f / size.height)))
        return minimum..maximum
    }

    fun frame(control: RemoteControl, raw: ControlPlacement, canvasWidth: Float, canvasHeight: Float = CanvasHeight): ControlFrame {
        val placement = raw.sanitized(RemoteLayout.Default[control])
        val size = baseSize(control, canvasWidth)
        val range = scaleRange(control, canvasWidth, canvasHeight)
        val scale = placement.scale.coerceIn(range.start, range.endInclusive)
        val width = size.width * scale
        val height = size.height * scale
        return ControlFrame(placement.x * (canvasWidth - width).coerceAtLeast(0f),
            placement.y * (canvasHeight - height).coerceAtLeast(0f), width, height)
    }

    fun move(control: RemoteControl, placement: ControlPlacement, dx: Float, dy: Float, canvasWidth: Float, canvasHeight: Float = CanvasHeight): ControlPlacement {
        val frame = frame(control, placement, canvasWidth, canvasHeight)
        val horizontal = canvasWidth - frame.width
        val vertical = canvasHeight - frame.height
        return placement.copy(
            x = if (horizontal > 0f) (frame.left + dx) / horizontal else 0f,
            y = if (vertical > 0f) (frame.top + dy) / vertical else 0f
        ).sanitized(RemoteLayout.Default[control])
    }
}
