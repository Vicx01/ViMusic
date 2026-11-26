package it.vfsfitvnm.vimusic.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metrolist.innertubex.extraction.ExtractedStream
import it.vfsfitvnm.vimusic.service.innertubex.ModernDataSpecResolver
import it.vfsfitvnm.vimusic.service.innertubex.StreamSnapshot
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class PlaybackProviderIntegrationTest {
    private fun stream(number: Int, itag: Int = 251, expiry: Long? = 100_000) = StreamSnapshot.from(
        ExtractedStream(videoId = "song", audioUrl = "https://fixture.invalid/$number",
            headers = mapOf("X-Stream" to "fixture"), loudnessDb = null,
            expiresAt = expiry?.let { Instant.fromEpochMilliseconds(it) }, contentLengthBytes = 1_000_000,
            itag = itag, mimeType = "audio/webm", codecs = "opus", bitrate = 128_000,
            sampleRate = 48_000, clientName = "fixture", profileId = "fixture",
            requireBoundedRange = false, rangeChunkSizeBytes = 0, useRangeChunks = false)
    )
    private fun spec(position: Long = 0) = DataSpec.Builder().setUri("song").setKey("song")
        .setPosition(position).build()

    private class FakeSource(val status: (DataSpec) -> Int) : DataSource {
        val opened = mutableListOf<DataSpec>()
        var closes = 0
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun getUri(): Uri? = opened.lastOrNull()?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
        override fun close() { closes++ }
        override fun open(dataSpec: DataSpec): Long {
            opened.add(dataSpec)
            val code = status(dataSpec)
            if (code != 200) throw HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), dataSpec, byteArrayOf())
            return 100L
        }
    }

    @Test fun providerSelectionUsesOnlySelectedFactory() {
        var legacyCalls = 0
        var modernCalls = 0
        val legacy = DataSource.Factory { FakeSource { 200 } }
        val modern = DataSource.Factory { FakeSource { 200 } }
        assertSame(legacy, selectPlaybackFactory(false, { legacyCalls++; legacy }, { modernCalls++; modern }))
        assertEquals(1, legacyCalls)
        assertEquals(0, modernCalls)
        assertSame(modern, selectPlaybackFactory(true, { legacyCalls++; legacy }, { modernCalls++; modern }))
        assertEquals(1, legacyCalls)
        assertEquals(1, modernCalls)
    }

    @Test fun tokenFailureFallsBackToLegacyWithoutHidingFailure() {
        var reported = 0
        var legacyCalls = 0
        val legacy = ResolvingDataSource.Resolver { legacyCalls++; it.withUri(Uri.parse("https://fixture.invalid/legacy")) }
        val resolver = ModernDataSpecResolver({ _, _ -> false }, { throw IOException("token fixture") }, legacy, { reported++ })
        val fake = FakeSource { 200 }
        val source = RecoveringPlaybackDataSource(fake, resolver, legacy, {}, ConcurrentHashMap())
        assertEquals(100L, source.open(spec()))
        assertEquals("https://fixture.invalid/legacy", fake.opened.single().uri.toString())
        assertEquals(1, reported)
        assertEquals(1, legacyCalls)
        source.close()
    }

    @Test fun stream403And416RefreshAndPreserveSeekRange() {
        var resolves = 0
        var refreshes = 0
        val legacy = ResolvingDataSource.Resolver { fail("unexpected fallback"); it }
        val resolver = ModernDataSpecResolver({ _, _ -> false }, { resolves++; stream(resolves) }, legacy, { fail("unexpected failure") }, { 0L })
        var calls = 0
        val fake = FakeSource { calls++; when (calls) { 1 -> 403; 3 -> 416; else -> 200 } }
        val source = RecoveringPlaybackDataSource(fake, resolver, legacy, { refreshes++ }, ConcurrentHashMap())
        assertEquals(100L, source.open(spec(600_000)))
        source.close()
        assertEquals(100L, source.open(spec(700_000)))
        assertEquals(3, resolves)
        assertEquals(2, refreshes)
        assertEquals(listOf(600_000L, 600_000L, 700_000L, 700_000L), fake.opened.map { it.position })
        assertEquals(listOf("/1", "/2", "/2", "/3"), fake.opened.map { it.uri.path })
        assertTrue(fake.opened.all { it.key == "innertubex:song:251" && it.httpRequestHeaders["X-Stream"] == "fixture" })
        source.close()
    }

    @Test fun rejectedInitialStreamCanFallBackButChangedFormatCannotBeMixed() {
        var resolves = 0
        var legacyCalls = 0
        val legacy = ResolvingDataSource.Resolver { legacyCalls++; it.withUri(Uri.parse("https://fixture.invalid/legacy")) }
        val resolver = ModernDataSpecResolver({ _, _ -> false }, { resolves++; stream(resolves) }, legacy, {}, { 0L })
        val fake = FakeSource { if (it.key == "song") 200 else 403 }
        val source = RecoveringPlaybackDataSource(fake, resolver, legacy, {}, ConcurrentHashMap())
        assertEquals(100L, source.open(spec()))
        assertEquals(1, legacyCalls)
        assertEquals(listOf("innertubex:song:251", "innertubex:song:251", "song"), fake.opened.map { it.key })
        source.close()

        var n = 0
        val changed = ModernDataSpecResolver({ _, _ -> false }, { n++; stream(n, if (n == 1) 251 else 140) }, legacy, {}, { 0L })
        var requests = 0
        val second = FakeSource { requests++; if (requests == 2) 403 else 200 }
        val checked = RecoveringPlaybackDataSource(second, changed, legacy, {}, ConcurrentHashMap())
        assertEquals(100L, checked.open(spec()))
        checked.close()
        try { checked.open(spec(500)); fail("Expected incompatible representation") } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("representation changed"))
        }
        assertEquals(2, second.opened.size) // different itag was never opened
    }

    @Test fun cancellationDoesNotFallBackOrRetry() {
        var fallback = 0
        val legacy = ResolvingDataSource.Resolver { fallback++; it }
        val resolver = ModernDataSpecResolver({ _, _ -> false }, { throw CancellationException("cancel") }, legacy, { fail("must not report cancellation") })
        val fake = FakeSource { 200 }
        val source = RecoveringPlaybackDataSource(fake, resolver, legacy, {}, ConcurrentHashMap())
        try { source.open(spec()); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(0, fallback)
        assertTrue(fake.opened.isEmpty())
    }
}
