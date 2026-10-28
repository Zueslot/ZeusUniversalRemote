package com.zeus.remote.data

/** IP fields are single-line values; ignore pasted diagnostic paragraphs after the first line. */
fun singleLineDeviceAddress(value: String): String = value.trim().lineSequence().firstOrNull().orEmpty().trim()
