package com.zeus.remote.shield.apps

import com.zeus.shared.ShieldApp
import org.junit.Assert.*
import org.junit.Test

class FavoriteAppMatcherTest {
    @Test fun resolvesInstalledVariantsAndPreservesManualAssignments() {
        val apps = listOf(ShieldApp("Nuvio (Legacy)", "space.nuvio.nativelegacy"),
            ShieldApp("SmartTube", "org.smarttube.stable"), ShieldApp("Reezn", "com.rmedia.rplayer"),
            ShieldApp("PLAYFy TV", "com.playfy.tv"), ShieldApp("My player", "custom.player"))
        assertEquals("space.nuvio.nativelegacy", matchFavorite("Nuvio", apps, null)?.packageName)
        assertEquals("org.smarttube.stable", matchFavorite("SmartTube", apps, null)?.packageName)
        assertEquals("com.rmedia.rplayer", matchFavorite("Reezen", apps, null)?.packageName)
        assertEquals("com.playfy.tv", matchFavorite("PLAYFy TV", apps, null)?.packageName)
        assertEquals("custom.player", matchFavorite("Nuvio", apps, "custom.player")?.packageName)
    }
    @Test fun neverInventsMissingOrAmbiguousMatches() {
        assertNull(matchFavorite("Reezen", emptyList(), null))
        assertNull(matchFavorite("Reezen", listOf(ShieldApp("Reezn", "first.player"), ShieldApp("Reezn", "other.player")), null))
    }
}
