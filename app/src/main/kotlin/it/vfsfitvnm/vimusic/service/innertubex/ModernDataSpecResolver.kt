package it.vfsfitvnm.vimusic.service.innertubex

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.CancellationException

/**
 * Bridge for progressive streams only. This is not selected by PlayerService yet:
 * rejection-triggered invalidation and the token-provider lifecycle must be wired first.
 * Neither stream credentials nor caught exception messages may be logged by callers.
 */
internal class ModernDataSpecResolver(
    private val isCached: (String, Long) -> Boolean,
    private val resolve: (String) -> StreamSnapshot,
    private val fallback: ResolvingDataSource.Resolver,
    private val onModernFailure: () -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ResolvingDataSource.Resolver {
    private val cache = StreamCache(initialGeneration = 0L)

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val id = dataSpec.key ?: error("A key must be set")
        if (isCached(id, dataSpec.position)) return dataSpec
        val now = nowMs()
        val stream = try {
            cache.get(id, 0L, safeCacheTime(now)) ?: resolve(id).also {
                check(!it.requiresChunkTransport) { "Unsupported stream transport" }
                check(Uri.parse(it.url).scheme == "https") { "Unsupported stream URL scheme" }
                check(it.expiresAtMs == null || it.expiresAtMs > now) { "Expired stream" }
                check(it.headers.keys.none { key -> key.equals("Range", ignoreCase = true) }) {
                    "Stream headers must not override requested byte range"
                }
                cache.put(id, 0L, it, safeCacheTime(now))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: Exception) {
            cache.invalidate(id, 0L)
            onModernFailure()
            return fallback.resolveDataSpec(dataSpec)
        }
        // DataSpec's own request headers survive, with provider-specific headers
        // winning case-insensitively. Position, URI offset, length, key and flags
        // come directly from the original request, so seeking is not truncated.
        val headers = dataSpec.httpRequestHeaders.filterKeys { key ->
            stream.headers.keys.none { it.equals(key, ignoreCase = true) }
        } + stream.headers
        return dataSpec.buildUpon()
            .setUri(stream.url)
            .setHttpRequestHeaders(headers)
            .build()
    }

    /** The transport owner must call this after rejecting the corresponding stream. */
    fun invalidate(videoId: String) = cache.invalidate(videoId, 0L)

    private fun safeCacheTime(now: Long): Long =
        if (now > Long.MAX_VALUE - EXPIRY_SKEW_MS) Long.MAX_VALUE else now + EXPIRY_SKEW_MS

    companion object {
        private const val EXPIRY_SKEW_MS = 30_000L
    }
}
