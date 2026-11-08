package it.vfsfitvnm.vimusic.service

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import it.vfsfitvnm.innertube.requests.PlayerRequestException
import it.vfsfitvnm.innertube.requests.PlayerRequestStage
import java.io.IOException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackRegressionTest {
    private val streamUri = Uri.parse("https://fixture.invalid/audio")

    @Test
    fun resolutionPreservesUnboundedAndBoundedRequestsOnMissAndRingHit() {
        var resolutions = 0
        val resolver = PlaybackDataSpecResolver(
            isCached = { _, _ -> false },
            resolveUri = { resolutions++; streamUri }
        )
        val initial = DataSpec.Builder().setUri("fixture").setKey("song").build()
        val first = resolver.resolveDataSpec(initial)
        assertEquals(streamUri, first.uri)
        assertEquals(C.LENGTH_UNSET.toLong(), first.length)
        assertEquals(0L, first.position)

        // An extractor seek or retry must retain its own offset and length, not add
        // the URI position offset a second time or impose a 512 KiB chunk.
        val requested = initial.buildUpon()
            .setPosition(600_000L).setUriPositionOffset(10L).setLength(12_345L).build()
        val next = resolver.resolveDataSpec(requested)
        assertEquals(600_000L, next.position)
        assertEquals(10L, next.uriPositionOffset)
        assertEquals(12_345L, next.length)
        assertEquals(streamUri, next.uri)
        assertEquals(1, resolutions)

        val cached = PlaybackDataSpecResolver({ _, _ -> true }, { error("unexpected resolve") })
        assertEquals(requested, cached.resolveDataSpec(requested))
    }

    @Test
    fun errorDetailsIncludeHttpStatusAndCauseWithoutSignedUrls() {
        val dataSpec = DataSpec.Builder().setUri("https://example.test/?secret=do-not-show").build()
        val response = HttpDataSource.InvalidResponseCodeException(
            403, "do-not-show", null, emptyMap(), dataSpec, "do-not-show".toByteArray()
        )
        val error = PlaybackException("do-not-show", response, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        val text = playbackErrorDetails(error)
        assertTrue(text.contains("HTTP 403"))
        assertTrue(text.contains("InvalidResponseCodeException"))
        assertTrue(text.contains("ERROR_CODE_IO_BAD_HTTP_STATUS"))
        assertFalse(text.contains("do-not-show"))
        assertFalse(text.contains("example.test"))
    }

    @Test
    fun resolverHttpErrorIsSanitizedAndNotRetried() {
        val rawCause = IllegalStateException("https://secret.invalid/?token=do-not-show")
        val failure = PlayerRequestException(
            PlayerRequestStage.EMBEDDED_PLAYER, 403, "code=403, status=PERMISSION_DENIED",
            "music.youtube.com", true, rawCause
        )
        val error = PlaybackException("do-not-show", failure, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        val text = playbackErrorDetails(error)
        assertTrue(text.contains("InnerTube player (embedded client)"))
        assertTrue(text.contains("HTTP 403"))
        assertTrue(text.contains("PERMISSION_DENIED"))
        assertTrue(text.contains("PlayerRequestException → IllegalStateException"))
        assertFalse(text.contains("do-not-show"))
        assertFalse(text.contains("secret.invalid"))
        val info = LoadErrorHandlingPolicy.LoadErrorInfo(
            LoadEventInfo(1, DataSpec(Uri.parse("fixture")), 0), MediaLoadData(C.DATA_TYPE_MEDIA), failure, 1
        )
        val policy = PlaybackLoadErrorHandlingPolicy()
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info))
        val ordinary = LoadErrorHandlingPolicy.LoadErrorInfo(info.loadEventInfo, info.mediaLoadData, IOException(), 1)
        assertEquals(0L, policy.getRetryDelayMsFor(ordinary))
    }

    @Test
    fun httpRangesAndResponseHeadersFollowTheRequestedDataSpec() {
        val data = ByteArray(800_000) { (it % 251).toByte() }
        RangeTestServer(data).use { server ->
            val resolver = PlaybackDataSpecResolver({ _, _ -> false }, { Uri.parse(server.url) })
            val source = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory(), resolver).createDataSource()
            val request = DataSpec.Builder().setUri("fixture").setKey("song")
                .setPosition(524_288L).setLength(16_384L)
                .setHttpRequestHeaders(mapOf("X-Regression-Header" to "fixture-value")).build()
            try {
                assertEquals(16_384L, source.open(request))
                val headers = source.responseHeaders
                fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
                assertEquals("16384", header("Content-Length"))
                assertEquals("bytes 524288-540671/800000", header("Content-Range"))
                assertEquals("bytes", header("Accept-Ranges"))
                val output = ByteArray(16_384)
                var total = 0
                while (total < output.size) {
                    val count = source.read(output, total, output.size - total)
                    if (count == C.RESULT_END_OF_INPUT) break
                    total += count
                }
                assertEquals(output.size, total)
                assertTrue(output.contentEquals(data.copyOfRange(524_288, 540_672)))
                assertEquals("bytes=524288-540671", server.ranges.single())
                assertEquals("fixture-value", server.regressionHeaders.single())
                source.close()

                val continuation = request.buildUpon().setPosition(540_672L).setLength(C.LENGTH_UNSET.toLong()).build()
                assertEquals(259_328L, source.open(continuation))
                val nextRange = source.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull()
                assertEquals("bytes 540672-799999/800000", nextRange)
                assertEquals("bytes=540672-", server.ranges.last())
                assertEquals("fixture-value", server.regressionHeaders.last())
                source.close()

                val pastEnd = request.buildUpon().setPosition(800_001L).setLength(C.LENGTH_UNSET.toLong()).build()
                try {
                    source.open(pastEnd)
                    fail("Expected HTTP 416")
                } catch (e: HttpDataSource.InvalidResponseCodeException) {
                    assertEquals(416, e.responseCode)
                }
                assertEquals(listOf(206, 206, 416), server.statuses.toList())
            } finally {
                source.close()
            }
        }
    }

    @Test(timeout = 90_000)
    fun continuousPlaybackPassesFirst512KiBAndThirtySeconds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = instrumentation.context.assets.open("playback-regression.m4a").use { it.readBytes() }
        assertTrue("fixture must exceed old 512 KiB boundary", bytes.size > 512 * 1024)
        val directory = File(context.cacheDir, "playback-test-${System.nanoTime()}")
        val cache = SimpleCache(directory, NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
        val server = RangeTestServer(bytes)
        val resolver = PlaybackDataSpecResolver(
            isCached = { id, position -> cache.isCached(id, position, 512 * 1024L) },
            resolveUri = { Uri.parse(server.url) }
        )
        val cacheFactory = CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
        val sources = ResolvingDataSource.Factory(cacheFactory, resolver)
        val extractors = ExtractorsFactory { arrayOf(MatroskaExtractor(), FragmentedMp4Extractor()) }
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                val instance = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(sources, extractors))
                    .build()
                player = instance
                instance.setMediaItem(
                    MediaItem.Builder().setMediaId("song").setUri("fixture")
                        .setCustomCacheKey("song").setMimeType("audio/mp4").build()
                )
                instance.prepare()
                instance.play()
            }
            var position = 0L
            var state = Player.STATE_IDLE
            var error: PlaybackException? = null
            val deadline = SystemClock.elapsedRealtime() + 75_000
            while (position < 37_000 && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    val instance = checkNotNull(player)
                    position = instance.currentPosition
                    state = instance.playbackState
                    error = instance.playerError
                }
                if (error != null || state == Player.STATE_ENDED) break
                SystemClock.sleep(200)
            }
            assertTrue("player error: ${error?.errorCodeName}", error == null)
            assertTrue("playback ended or stalled at $position ms (state $state)", position >= 37_000)
            assertFalse("playback ended prematurely", state == Player.STATE_ENDED)
            assertEquals("initial request must not be capped at 512 KiB", "none", server.ranges.first())
            assertEquals(200, server.statuses.first())
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            cache.release()
            server.close()
            directory.deleteRecursively()
        }
    }
}
