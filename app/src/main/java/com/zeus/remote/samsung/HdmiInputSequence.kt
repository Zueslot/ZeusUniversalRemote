package com.zeus.remote.samsung

/** Menu layout and timing verified on the owner's S95B; preserve these during UI refactors. */
internal object HdmiInputSequence {
    val keys = listOf("KEY_SOURCE") + List(10) { "KEY_LEFT" } + List(3) { "KEY_RIGHT" } + "KEY_ENTER"

    fun pauseAfter(index: Int, fast: Boolean): Long = when {
        !fast -> 2000L
        keys[index] == "KEY_SOURCE" -> 2000L
        index == keys.lastIndex - 1 -> 1000L
        keys[index] == "KEY_ENTER" -> 500L
        else -> 100L
    }
}
