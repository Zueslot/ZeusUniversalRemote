package com.zeus.remote.samsung

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guard the physical-device-tested macro across the feature extraction. */
class HdmiInputSequenceTest {
    @Test fun preservesVerifiedMenuRoute() {
        assertEquals(listOf("KEY_SOURCE") + List(10) { "KEY_LEFT" } +
            List(3) { "KEY_RIGHT" } + "KEY_ENTER", HdmiInputSequence.keys)
    }

    @Test fun preservesBothVerifiedTimingModes() {
        val fast = HdmiInputSequence.keys.indices.map { HdmiInputSequence.pauseAfter(it, true) }
        assertEquals(2000L, fast.first())
        assertEquals(List(12) { 100L }, fast.subList(1, 13))
        assertEquals(1000L, fast[13])
        assertEquals(500L, fast.last())
        assertEquals(6700L, 2000L + fast.sum())
        assertEquals(32000L, 2000L + HdmiInputSequence.keys.indices.sumOf { HdmiInputSequence.pauseAfter(it, false) })
    }
}
