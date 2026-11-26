package it.vfsfitvnm.vimusic.service

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

/** Resolves a media ID without changing the byte range requested by the player. */
internal class PlaybackDataSpecResolver(
    private val isCached: (videoId: String, position: Long) -> Boolean,
    private val resolveUri: (videoId: String) -> Uri
) : ResolvingDataSource.Resolver {
    private val urls = object : LinkedHashMap<String, Uri>(2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Uri>?): Boolean = size > 2
    }

    @Synchronized
    fun invalidate(videoId: String) { urls.remove(videoId) }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = dataSpec.key ?: error("A key must be set")
        if (isCached(videoId, dataSpec.position)) return dataSpec

        val uri = synchronized(this) { urls[videoId] } ?: resolveUri(videoId).also {
            synchronized(this) { urls[videoId] = it }
        }

        // ResolvingDataSource sits outside CacheDataSource: shortening this DataSpec
        // would signal end-of-input to the extractor, not request the next chunk.
        return dataSpec.withUri(uri)
    }
}
