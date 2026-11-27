package it.vfsfitvnm.vimusic.service.innertubex

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import io.ktor.client.HttpClient

/**
 * Playback extraction adapter. The caller owns the
 * HttpClient and token provider. Never log stream URLs, headers or tokens.
 */
internal class PlaybackAdapter(client: HttpClient, tokenProvider: TokenProvider) {
    private val innerTube = InnerTube(client)
    private val config = RemotePlayerConfigStore(client, PlayerConfigRepository.disabled(), InnerTubeLogger.NONE)
    private val cipher = YouTubeCipherService(client, config, InnerTubeLogger.NONE)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(client, innerTube, config, InnerTubeLogger.NONE),
        cipherService = cipher,
        innerTube = innerTube,
        tokenProvider = tokenProvider,
        logger = InnerTubeLogger.NONE,
    )

    /** Releases library-owned work; the caller still owns the client and token provider. */
    suspend fun close() {
        try {
            cipher.dispose()
        } finally {
            innerTube.close()
        }
    }

    suspend fun refreshAfterStreamRejection(): Boolean = cipher.refreshAfterStreamRejection()

    /** Network is used only when an explicit caller invokes this method. */
    suspend fun resolve(videoId: String, clientOverrideId: String? = null): StreamSnapshot {
        val stream = requireNotNull(extractor.extract(
            videoId = videoId,
            hints = ContentHints(wantVideo = false, playbackClientOverrideId = clientOverrideId).withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                // A range-chunking DataSource has not yet been implemented.
                allowBoundedRange = false,
            ),
            audioQuality = AudioQuality.AUTO,
        )) { "No playable stream" }
        val snapshot = StreamSnapshot.from(stream)
        check(!snapshot.requiresChunkTransport) { "Range-chunk transport is not implemented" }
        return snapshot
    }
}
