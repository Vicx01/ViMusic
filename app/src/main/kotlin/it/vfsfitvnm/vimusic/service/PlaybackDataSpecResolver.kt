package it.vfsfitvnm.vimusic.service

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import it.vfsfitvnm.vimusic.utils.RingBuffer

/** Resolves a media ID without changing the byte range requested by the player. */
internal class PlaybackDataSpecResolver(
    private val isCached: (videoId: String, position: Long) -> Boolean,
    private val resolveUri: (videoId: String) -> Uri
) : ResolvingDataSource.Resolver {
    private val urls = RingBuffer<Pair<String, Uri>?>(2) { null }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = dataSpec.key ?: error("A key must be set")
        if (isCached(videoId, dataSpec.position)) return dataSpec

        val uri = (0..1).firstNotNullOfOrNull { index ->
            urls.getOrNull(index)?.takeIf { it.first == videoId }?.second
        } ?: resolveUri(videoId).also { urls.append(videoId to it) }

        // ResolvingDataSource sits outside CacheDataSource: shortening this DataSpec
        // would signal end-of-input to the extractor, not request the next chunk.
        return dataSpec.withUri(uri)
    }
}
