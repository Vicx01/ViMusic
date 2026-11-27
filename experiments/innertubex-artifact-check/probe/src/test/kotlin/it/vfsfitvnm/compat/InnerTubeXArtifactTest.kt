package it.vfsfitvnm.compat

import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Compiles against and executes the actual resolved InnerTubeX artifact; no network request. */
class InnerTubeXArtifactTest {
    @Test
    fun actualArtifactSupportsDirectAudioHints() {
        val hints = ContentHints(wantVideo = false).withStreamCapabilities(
            allowHls = false,
            allowSabr = false,
            allowBoundedRange = false,
        )
        assertFalse(hints.wantVideo)
        assertFalse(hints.allowHls)
        assertFalse(hints.allowSabr)
        assertFalse(hints.allowBoundedRange)
        assertEquals("AUTO", AudioQuality.AUTO.name)
    }
}
