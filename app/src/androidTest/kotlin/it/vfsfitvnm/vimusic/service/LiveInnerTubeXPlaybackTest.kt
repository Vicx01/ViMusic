package it.vfsfitvnm.vimusic.service

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.vfsfitvnm.vimusic.service.innertubex.ModernDataSpecResolver
import it.vfsfitvnm.vimusic.service.innertubex.ModernPlayback
import it.vfsfitvnm.vimusic.service.innertubex.safeModernFailure
import it.vfsfitvnm.vimusic.service.innertubex.safeStreamClient
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opted-in live network probe. Never uses a user's account, queue or media ID. */
@RunWith(AndroidJUnit4::class)
class LiveInnerTubeXPlaybackTest {
    @Test(timeout = 210_000)
    fun liveInnerTubeXPlaybackOverThirtySeconds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("vimusicLive") == "1")
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "live-probe-${System.nanoTime()}")
        val cache = SimpleCache(directory, NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
        val modern = ModernPlayback(context)
        val fallbacks = AtomicInteger()
        val resolved = AtomicInteger()
        val refreshes = AtomicInteger()
        val legacy = ResolvingDataSource.Resolver {
            fallbacks.incrementAndGet()
            throw IOException("Live probe requires InnerTubeX; legacy was not attempted")
        }
        val resolver = ModernDataSpecResolver({ _, _ -> false }, { id ->
            runBlocking(Dispatchers.IO) { modern.resolve(id) }.also {
                resolved.incrementAndGet()
                Log.i("ViMusicPlayback", "LIVE_RESOLVED client=${safeStreamClient(it.clientName)} itag=${it.itag}")
            }
        }, legacy, { Log.w("ViMusicPlayback", "LIVE_RESOLVE_FAILED ${safeModernFailure(it)}") })
        val cacheFactory = CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setConnectTimeoutMs(16_000).setReadTimeoutMs(8_000))
        val factory = recoveringPlaybackFactory(cacheFactory, resolver, legacy) {
            refreshes.incrementAndGet()
            runBlocking(Dispatchers.IO) { modern.refreshAfterRejection() }
        }
        val extractors = ExtractorsFactory { arrayOf(MatroskaExtractor(), FragmentedMp4Extractor()) }
        var player: ExoPlayer? = null
        var position = 0L
        var state = Player.STATE_IDLE
        var failure: PlaybackException? = null
        try {
            instrumentation.runOnMainSync {
                installPlaybackMedia3Log()
                val instance = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory, extractors)).build()
                player = instance
                // Existing public QuickPicks fixture; no private history or user credentials.
                instance.setMediaItem(MediaItem.Builder().setMediaId("J7p4bzqLvCw").setUri("J7p4bzqLvCw")
                    .setCustomCacheKey("J7p4bzqLvCw").build())
                instance.prepare()
                instance.play()
            }
            val deadline = SystemClock.elapsedRealtime() + 160_000
            while (position < 35_000 && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { checkNotNull(player).let { position = it.currentPosition; state = it.playbackState; failure = it.playerError } }
                if (failure != null || state == Player.STATE_ENDED) break
                SystemClock.sleep(200)
            }
            var status = 0
            var cause: Throwable? = failure
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            while (cause != null && seen.add(cause)) {
                if (cause is HttpDataSource.InvalidResponseCodeException) status = cause.responseCode
                cause = cause.cause
            }
            val result = if (position >= 35_000 && failure == null && fallbacks.get() == 0) "passed" else "failed"
            Log.i("ViMusicPlayback", "LIVE_PROBE result=$result positionMs=$position state=$state error=${failure?.errorCodeName ?: "none"} http=$status resolved=${resolved.get()} fallback=${fallbacks.get()} refresh=${refreshes.get()}")
            if (result != "passed") throw AssertionError("Live InnerTubeX probe failed; see sanitized LIVE_PROBE diagnostics")
        } catch (failure: AssertionError) {
            throw failure
        } catch (failure: Throwable) {
            Log.w("ViMusicPlayback", "LIVE_PROBE result=failed phase=unexpected")
            throw AssertionError("Live InnerTubeX probe failed unexpectedly; sensitive details omitted")
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            runBlocking(Dispatchers.IO) { modern.close() }
            cache.release()
            directory.deleteRecursively()
        }
    }
}
