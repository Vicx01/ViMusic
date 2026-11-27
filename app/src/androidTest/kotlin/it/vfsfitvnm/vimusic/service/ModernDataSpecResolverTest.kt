package it.vfsfitvnm.vimusic.service

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metrolist.innertubex.extraction.ExtractedStream
import it.vfsfitvnm.vimusic.service.innertubex.ModernDataSpecResolver
import it.vfsfitvnm.vimusic.service.innertubex.StreamSnapshot
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class ModernDataSpecResolverTest {
    private fun stream(expiry: Long? = 100_000L, headers: Map<String, String> = mapOf("X-Stream" to "fixture"), bounded: Boolean = false) =
        StreamSnapshot.from(ExtractedStream(
            videoId = "fixture", audioUrl = "https://fixture.invalid/audio", headers = headers,
            loudnessDb = null, expiresAt = expiry?.let { Instant.fromEpochMilliseconds(it) },
            contentLengthBytes = 1_000_000, itag = 251, mimeType = "audio/webm", codecs = "opus",
            bitrate = 128_000, sampleRate = 48_000, clientName = "fixture", profileId = "fixture",
            requireBoundedRange = bounded, rangeChunkSizeBytes = if (bounded) 1024 else 0,
            useRangeChunks = false,
        ))
    private fun spec() = DataSpec.Builder().setUri("fixture").setKey("song")
        .setPosition(600_000).setUriPositionOffset(10).setLength(12_345)
        .setHttpRequestHeaders(mapOf("x-stream" to "old", "X-Other" to "kept")).build()

    @Test fun modernBridgePreservesRangeHeadersAndCacheExpiry() {
        var clock = 0L
        var resolutions = 0
        val resolver = ModernDataSpecResolver({ _, _ -> false }, { resolutions++; stream() },
            ResolvingDataSource.Resolver { error("unexpected fallback") }, { error("unexpected failure") }, { clock })
        val requested = spec()
        val result = resolver.resolveDataSpec(requested)
        assertEquals("https://fixture.invalid/audio", result.uri.toString())
        assertEquals(requested.position, result.position)
        assertEquals(requested.uriPositionOffset, result.uriPositionOffset)
        assertEquals(requested.length, result.length)
        assertEquals("innertubex:song:251", result.key)
        assertEquals(requested.flags, result.flags)
        assertEquals(mapOf("X-Stream" to "fixture", "X-Other" to "kept"), result.httpRequestHeaders)
        resolver.resolveDataSpec(requested)
        assertEquals(1, resolutions)
        clock = 70_000L
        resolver.resolveDataSpec(requested)
        assertEquals(2, resolutions)
        resolver.invalidate("song")
        resolver.resolveDataSpec(requested)
        assertEquals(3, resolutions)
    }

    @Test fun modernBridgeFallbackIsVisibleAndUnsafeStreamsAreRejected() {
        var failures = 0
        var fallbackCalls = 0
        val fallback = ResolvingDataSource.Resolver { fallbackCalls++; it }
        fun checkFallback(producer: () -> StreamSnapshot) {
            val resolver = ModernDataSpecResolver({ _, _ -> false }, { producer() }, fallback, { failures++ }, { 100L })
            val original = spec()
            assertEquals(original, resolver.resolveDataSpec(original))
        }
        checkFallback { throw IllegalStateException("synthetic failure") }
        checkFallback { stream(bounded = true) }
        checkFallback { stream(expiry = 100L) }
        checkFallback { stream(headers = mapOf("range" to "bytes=0-1")) }
        assertEquals(4, failures)
        assertEquals(4, fallbackCalls)
    }

    @Test fun modernBridgeRespectsAudioCacheAndCancellation() {
        val fallback = ResolvingDataSource.Resolver { error("unexpected fallback") }
        val cached = ModernDataSpecResolver({ _, _ -> true }, { error("unexpected resolution") }, fallback, { error("unexpected failure") })
        val original = spec()
        assertEquals(original, cached.resolveDataSpec(original))
        var failures = 0
        val cancelled = ModernDataSpecResolver({ _, _ -> false }, { throw CancellationException("cancel") }, fallback, { failures++ })
        try { cancelled.resolveDataSpec(original); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(0, failures)
        var resolves = 0
        val unknownExpiry = ModernDataSpecResolver({ _, _ -> false }, { resolves++; stream(expiry = null) }, fallback, { error("unexpected failure") }, { 0L })
        unknownExpiry.resolveDataSpec(original)
        unknownExpiry.resolveDataSpec(original)
        assertEquals(2, resolves)
    }
}
