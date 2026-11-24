package it.vfsfitvnm.vimusic.service.innertubex

import com.metrolist.innertubex.extraction.ExtractedStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class StreamSnapshotTest {
    private fun stream(bounded: Boolean = false, chunked: Boolean = false, expiry: Instant? = Instant.fromEpochMilliseconds(123456L)) = ExtractedStream(
        videoId = "fixture",
        audioUrl = "https://example.invalid/audio?signature=synthetic-secret",
        headers = mapOf("Authorization" to "synthetic-header-secret"),
        loudnessDb = null,
        expiresAt = expiry,
        contentLengthBytes = 1_000_000,
        itag = 251,
        mimeType = "audio/webm",
        codecs = "opus",
        bitrate = 128_000,
        sampleRate = 48_000,
        clientName = "fixture-client",
        profileId = "fixture-profile",
        requireBoundedRange = bounded,
        rangeChunkSizeBytes = if (bounded || chunked) 1024 else 0,
        useRangeChunks = chunked,
    )

    @Test fun preservesStreamFieldsButRedactsDiagnosticString() {
        val result = StreamSnapshot.from(stream())
        assertEquals(123456L, result.expiresAtMs)
        assertEquals(251, result.itag)
        assertEquals("fixture-client", result.clientName)
        assertEquals("synthetic-header-secret", result.headers["Authorization"])
        assertFalse(result.requiresChunkTransport)
        assertFalse(result.toString().contains("synthetic"))
        assertFalse(result.toString().contains("example.invalid"))
    }

    @Test fun explicitlyFlagsBothBoundedAndChunkedStreams() {
        assertTrue(StreamSnapshot.from(stream(bounded = true)).requiresChunkTransport)
        assertTrue(StreamSnapshot.from(stream(chunked = true)).requiresChunkTransport)
    }

    @Test fun cacheExpiresAndDoesNotCrossSessionGenerations() {
        val cache = StreamCache(initialGeneration = 1L)
        val value = StreamSnapshot.from(stream())
        cache.put("fixture", 1L, value, 100L)
        assertTrue(cache.get("fixture", 1L, 123455L) === value)
        assertEquals(null, cache.get("fixture", 1L, 123456L))
        cache.put("fixture", 1L, value, 100L)
        cache.changeSession(2L)
        assertEquals(null, cache.get("fixture", 2L, 100L))
        cache.put("fixture", 1L, value, 100L) // late completion from old session
        assertEquals(null, cache.get("fixture", 2L, 100L))
        cache.put("fixture", 2L, value, 100L)
        cache.invalidate("fixture", 1L) // old failure cannot evict current session
        assertTrue(cache.get("fixture", 2L, 100L) === value)
    }

    @Test fun unknownExpiryAndInvalidationDoNotReuseUrls() {
        val cache = StreamCache(initialGeneration = 1L)
        val value = StreamSnapshot.from(stream())
        cache.put("fixture", 1L, value, 100L)
        cache.put("fixture", 1L, StreamSnapshot.from(stream(expiry = null)), 100L)
        assertEquals(null, cache.get("fixture", 1L, 100L))
        cache.put("fixture", 1L, value, 100L)
        cache.invalidate("fixture", 1L)
        assertEquals(null, cache.get("fixture", 1L, 100L))
    }

}
