package it.vfsfitvnm.vimusic.service

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.ResolvingDataSource
import it.vfsfitvnm.vimusic.service.innertubex.ModernDataSpecResolver
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/**
 * Resolves before opening CacheDataSource. On 403/416 it refreshes once, then
 * tries legacy only before any bytes from a representation have been accepted.
 * Never combine bytes from different encodings after a successful open.
 */
internal class RecoveringPlaybackDataSource(
    private val upstream: DataSource,
    private val modern: ModernDataSpecResolver,
    private val legacy: ResolvingDataSource.Resolver,
    private val refresh: (String) -> Unit,
    private val acceptedKeys: MutableMap<String, String>,
) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)
    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)
    override fun close() = upstream.close()

    override fun open(dataSpec: DataSpec): Long {
        val id = dataSpec.key ?: throw IOException("A media key is required")
        var candidate = modern.resolveDataSpec(dataSpec)
        var lastFailure: HttpDataSource.InvalidResponseCodeException? = null
        for (attempt in 0..2) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Playback interrupted")
            val representation = candidate.key ?: throw IOException("A resolved cache key is required")
            val accepted = acceptedKeys[id]
            if (accepted != null && accepted != representation) {
                throw IOException("Stream representation changed; restart this track", lastFailure)
            }
            try {
                val result = upstream.open(candidate)
                val changed = synchronized(acceptedKeys) {
                    val previous = acceptedKeys[id]
                    if (previous == null) acceptedKeys[id] = representation
                    previous != null && previous != representation
                }
                if (changed) {
                    upstream.close()
                    throw IOException("Concurrent stream representation changed; restart this track")
                }
                return result
            } catch (failure: HttpDataSource.InvalidResponseCodeException) {
                if (failure.responseCode != 403 && failure.responseCode != 416) throw failure
                lastFailure = failure
                upstream.close()
                if (attempt == 2) throw failure
                modern.invalidate(id)
                if (attempt == 0) {
                    Log.w("ViMusicPlayback", "Stream rejected with HTTP ${failure.responseCode}; refreshing")
                    try {
                        refresh(id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw interrupted
                    } catch (error: Exception) {
                        Log.w("ViMusicPlayback", "Stream refresh failed; retrying resolution")
                    }
                    candidate = modern.resolveDataSpec(dataSpec)
                } else {
                    // A different provider can return a different codec/itag. Only
                    // fall back before the extractor has accepted this song.
                    if (acceptedKeys[id] != null || dataSpec.position != 0L) throw failure
                    Log.w("ViMusicPlayback", "Refreshed stream rejected; trying legacy provider")
                    candidate = legacy.resolveDataSpec(dataSpec)
                }
            }
        }
        throw checkNotNull(lastFailure)
    }
}

internal fun recoveringPlaybackFactory(
    upstream: DataSource.Factory,
    modern: ModernDataSpecResolver,
    legacy: ResolvingDataSource.Resolver,
    refresh: (String) -> Unit,
): DataSource.Factory {
    val accepted = ConcurrentHashMap<String, String>()
    return DataSource.Factory { RecoveringPlaybackDataSource(upstream.createDataSource(), modern, legacy, refresh, accepted) }
}

/** Select once per service lifetime so a settings change cannot mix bytes mid-track. */
internal fun selectPlaybackFactory(
    modernEnabled: Boolean,
    legacy: () -> DataSource.Factory,
    modern: () -> DataSource.Factory,
): DataSource.Factory = if (modernEnabled) modern() else legacy()
