package com.zeus.remote

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeus.remote.ui.DirectionPad
import com.zeus.remote.ui.RemoteIcon
import java.util.concurrent.atomic.AtomicInteger

/** Device smoke test using platform accessibility, without additional test dependencies. */
class RemoteSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val back = awaitNode("Back")
            val menu = awaitNode("Remote menu. Drag to move menu.")
            val backBounds = Rect().also { back.getBoundsInScreen(it) }
            val menuBounds = Rect().also { menu.getBoundsInScreen(it) }
            check(backBounds.centerX() < menuBounds.centerX()) { "Back must be left of Settings" }
            listOf("Navigate up", "Navigate down", "Navigate left", "Navigate right", "Shield Home", "Back").forEach {
                check(awaitNode(it).isEnabled) { "$it is disabled" }
            }
            touch("Remote menu. Drag to move menu.")
            awaitNode("Reconnect devices")
            click("Settings")
            awaitNode("DEVICES")
            click("Remote")
            awaitNode("Back")
            var connectionChecks = "Disconnected setup routing"
            // Unconfigured remote buttons must explain how to connect instead of doing nothing.
            if (find("Shield disconnected · Tap to connect") != null) {
                click("Shield Home")
                awaitNode("DEVICES")
                click("Remote")
                click("Back")
                awaitNode("DEVICES")
                click("Remote")
                click("Navigate left")
                awaitNode("DEVICES")
                click("Remote")
            } else {
                touch("Remote menu. Drag to move menu.")
                awaitNode("Samsung: connected\nShield: connected")
                uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                listOf("Navigate up", "Navigate down", "Navigate left", "Navigate right", "OK / Select", "Shield Home", "Back").forEach { touch(it) }
                touch("Navigate left", 750)
                touch("Navigate right", 750)
                touch("Navigate up", 750)
                touch("Navigate down", 750)
                touch("Remote menu. Drag to move menu.")
                awaitNode("Samsung: connected\nShield: connected")
                uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                connectionChecks = "Connected directional/OK/Home/Back touches, all four held directions, connections retained"
            }
            verifyDirectionRepeat(activity)
            result.putString("stream", "PASS: layout, enabled navigation, Settings touch, menu, Settings/Remote screens. $connectionChecks.\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun verifyDirectionRepeat(activity: MainActivity) {
        val counts = RemoteIcon.entries.associateWith { AtomicInteger() }
        runOnMainSync { activity.setContent {
            Box(Modifier.padding(start = 40.dp, top = 100.dp)) {
                DirectionPad(true, { counts.getValue(it).incrementAndGet() })
            }
        } }
        waitForIdleSync()
        // Accessibility updates arrive after the Compose frame replacing the full remote.
        val deadline = SystemClock.uptimeMillis() + 5000
        while (find("Back") != null && SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
        for ((label, icon) in listOf("up" to RemoteIcon.Up, "down" to RemoteIcon.Down,
                "left" to RemoteIcon.Left, "right" to RemoteIcon.Right)) {
            val counter = counts.getValue(icon)
            touch("Navigate $label")
            check(counter.get() == 1) { "Tap $label must send exactly one command, received ${counter.get()}" }
            touch("Navigate $label", 850)
            check(counter.get() >= 4) { "Held $label did not repeat: ${counter.get()}" }
            val releasedCount = counter.get()
            Thread.sleep(250)
            check(counter.get() == releasedCount) { "$label repeated after release" }
        }
        touch("OK / Select", 850)
        check(counts.getValue(RemoteIcon.Remote).get() == 1) { "OK must not repeat" }
    }

    private fun find(label: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it)?.let { match -> return match } }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::visit)
    }

    private fun awaitNode(label: String): AccessibilityNodeInfo {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            find(label)?.let { return it }
            Thread.sleep(100)
        }
        error("Missing UI: $label")
    }

    private fun click(label: String) {
        var node = awaitNode(label)
        while (!node.isClickable) node = node.parent ?: error("Not clickable: $label")
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Click failed: $label" }
        waitForIdleSync()
    }

    private fun touch(label: String, duration: Long = 80) {
        val bounds = Rect().also { awaitNode(label).getBoundsInScreen(it) }
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(uiAutomation.injectInputEvent(event, true)) { "Touch failed: $label" } }
            finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN)
        Thread.sleep(duration)
        inject(MotionEvent.ACTION_UP)
        waitForIdleSync()
    }
}
