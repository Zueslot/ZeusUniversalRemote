package com.zeus.remote.shield.apps

import com.zeus.shared.ShieldApp

internal fun matchFavorite(label: String, apps: List<ShieldApp>, assigned: String?): ShieldApp? {
    apps.find { it.packageName == assigned }?.let { return it }
    val knownPackages = when (label) {
        "SmartTube" -> listOf("org.smarttube.stable", "org.smarttube.beta")
        "Nuvio" -> listOf("space.nuvio.nativelegacy", "space.nuvio.native")
        "PLAYFy TV" -> listOf("com.playfy.tv")
        else -> emptyList()
    }
    knownPackages.forEach { pkg -> apps.find { it.packageName == pkg }?.let { return it } }
    fun normalize(value: String) = value.filter(Char::isLetterOrDigit).lowercase()
    val names = when (label) {
        "Reezen" -> setOf("reezen", "reezn")
        "Nuvio" -> setOf("nuvio", "nuviolegacy")
        else -> setOf(normalize(label))
    }
    return apps.singleOrNull { normalize(it.name) in names }
}
