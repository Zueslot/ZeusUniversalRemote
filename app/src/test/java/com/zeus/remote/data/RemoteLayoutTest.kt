package com.zeus.remote.data

import org.junit.Assert.*
import org.junit.Test

class RemoteLayoutTest {
    @Test fun previousLayoutRealignsOnceAndNewEditsArePreserved() {
        val previous = "{\"version\":2,\"controls\":{\"BACK\":{\"x\":0,\"y\":0,\"scale\":1.6}}}"
        assertEquals(RemoteLayout.Default, RemoteLayout.decode(previous))
        val edited = RemoteLayout.Default.update(RemoteControl.BACK, ControlPlacement(.1f, .2f, 1.2f))
        assertEquals(edited, RemoteLayout.decode(edited.encode()))
        assertFalse(ActiveRemoteControls.contains(RemoteControl.STREAMIO))
        assertEquals(listOf("SmartTube", "Nuvio", "PLAYFy TV", "Reezen"), FavoriteAppNames)
    }
    @Test fun fourAppPillsHaveMatchingSizesAndEvenNonOverlappingSpacing() {
        val apps = listOf(RemoteControl.SMARTTUBE, RemoteControl.NUVIO, RemoteControl.PLAYFY, RemoteControl.REEZEN)
        for (width in listOf(360f, 392f, 440f)) {
            val frames = apps.map { RemoteLayoutGeometry.frame(it, RemoteLayout.Default[it], width, 800f) }
            frames.forEach { assertEquals(76f, it.width, .01f); assertEquals(72f, it.height, .01f) }
            val gaps = frames.zipWithNext { a, b -> b.left - (a.left + a.width) }
            gaps.forEach { assertTrue(it >= 0); assertEquals(gaps.first(), it, .01f) }
            assertEquals(frames.first().top, frames.last().top, .01f)
            val home = RemoteLayoutGeometry.frame(RemoteControl.HOME, RemoteLayout.Default[RemoteControl.HOME], width, 800f)
            val back = RemoteLayoutGeometry.frame(RemoteControl.BACK, RemoteLayout.Default[RemoteControl.BACK], width, 800f)
            assertEquals(home.top, back.top, .01f)
            assertEquals(home.width, back.width, .01f)
        }
    }
    @Test fun savedLayoutRoundTripPreservesMovedAndResizedControls() {
        val layout = RemoteLayout.Default.update(RemoteControl.HOME, ControlPlacement(.2f, .3f, 1.4f))
            .update(RemoteControl.NUVIO, ControlPlacement(.8f, .7f, 1.2f))
        assertEquals(layout, RemoteLayout.decode(layout.encode()))
    }
    @Test fun corruptMissingOrFutureSettingsRecoverToSafeDefaults() {
        listOf("", "not-json", "{}", "{\"version\":99}").forEach { assertEquals(RemoteLayout.Default, RemoteLayout.decode(it)) }
        val partial = RemoteLayout.decode("{\"version\":1,\"controls\":{\"POWER\":{\"x\":0.1,\"y\":0.2,\"scale\":1.2}}}")
        assertEquals(RemoteLayout.Default[RemoteControl.DPAD], partial[RemoteControl.DPAD])
        assertEquals(.1f, partial[RemoteControl.POWER].x, .0001f)
    }
    @Test fun invalidCoordinatesAndSizesCannotEscapeCanvas() {
        val placement = ControlPlacement(-5f, 8f, Float.NaN).sanitized(RemoteLayout.Default[RemoteControl.HOME])
        assertEquals(0f, placement.x, 0f)
        assertEquals(1f, placement.y, 0f)
        assertEquals(1f, placement.scale, 0f)
        RemoteControl.entries.forEach { control ->
            for (width in listOf(264f, 320f, 392f, 440f)) {
                for (scale in listOf(.1f, 1f, 5f)) {
                    val frame = RemoteLayoutGeometry.frame(control, ControlPlacement(1f, 1f, scale), width)
                    assertTrue(frame.width >= 47.99f && frame.height >= 47.99f)
                    assertTrue(frame.left >= 0f && frame.top >= 0f)
                    assertTrue(frame.left + frame.width <= width + .01f)
                    assertTrue(frame.top + frame.height <= RemoteLayoutGeometry.CanvasHeight + .01f)
                }
            }
        }
    }
    @Test fun draggingClampsAtBothEdgesAcrossDisplayWidths() {
        for (width in listOf(264f, 360f, 440f)) {
            val start = RemoteLayout.Default[RemoteControl.DPAD].copy(scale = 1f)
            val right = RemoteLayoutGeometry.move(RemoteControl.DPAD, start, 10000f, 10000f, width)
            assertEquals(1f, right.x, 0f)
            assertEquals(1f, right.y, 0f)
            val left = RemoteLayoutGeometry.move(RemoteControl.DPAD, right, -10000f, -10000f, width)
            assertEquals(0f, left.x, 0f)
            assertEquals(0f, left.y, 0f)
        }
    }
    @Test fun unsavedDraftChangesCannotMutateSavedLayout() {
        val saved = RemoteLayout.Default
        val draft = saved.update(RemoteControl.POWER, ControlPlacement(0f, 1f, 1.4f))
        assertNotEquals(saved[RemoteControl.POWER], draft[RemoteControl.POWER])
        assertEquals(ControlPlacement(1f, 0f), saved[RemoteControl.POWER])
    }
    @Test fun compositeControlsKeepTheirIndividualButtonTouchTargets() {
        listOf(RemoteControl.DPAD, RemoteControl.VOLUME, RemoteControl.MEDIA).forEach { control ->
            assertTrue(RemoteLayoutGeometry.scaleRange(control, 320f).start >= 1f)
        }
    }
    @Test fun allFourEdgesAreReachableInTheFullAvailableViewport() {
        for ((width, height) in listOf(392f to 800f, 800f to 360f, 320f to 650f)) {
            RemoteControl.entries.forEach { control ->
                val upperLeft = RemoteLayoutGeometry.frame(control, ControlPlacement(0f, 0f), width, height)
                assertEquals(0f, upperLeft.left, .01f)
                assertEquals(0f, upperLeft.top, .01f)
                val lowerRight = RemoteLayoutGeometry.frame(control, ControlPlacement(1f, 1f), width, height)
                assertEquals(width, lowerRight.left + lowerRight.width, .01f)
                assertEquals(height, lowerRight.top + lowerRight.height, .01f)
                val dragged = RemoteLayoutGeometry.move(control, ControlPlacement(.5f, .5f), 10000f, 10000f, width, height)
                assertEquals(1f, dragged.x, .01f)
                assertEquals(1f, dragged.y, .01f)
            }
        }
    }
    @Test fun olderLayoutsMakeRoomForBrandingWithoutResettingOtherControls() {
        val old = "{\"version\":1,\"controls\":{\"POWER\":{\"x\":0,\"y\":0,\"scale\":1.2},\"HOME\":{\"x\":0.2,\"y\":0.7}}}"
        val migrated = RemoteLayout.decode(old)
        assertEquals(1f, migrated[RemoteControl.POWER].x, 0f)
        assertEquals(1.2f, migrated[RemoteControl.POWER].scale, 0f)
        assertEquals(.2f, migrated[RemoteControl.HOME].x, 0f)
        assertEquals(.7f, migrated[RemoteControl.HOME].y, 0f)
        assertEquals(ControlPlacement(0f, 0f), migrated[RemoteControl.BRANDING])
    }
}
