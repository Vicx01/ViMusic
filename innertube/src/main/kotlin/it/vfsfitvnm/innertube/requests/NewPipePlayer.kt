package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.utils.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream

internal fun AudioStream.toAdaptiveFormat(durationMs: Long?) =
    PlayerResponse.StreamingData.AdaptiveFormat(
        itag = itag,
        mimeType = format?.mimeType ?: "audio/mp4",
        bitrate = bitrate.takeUnless { it < 0 }?.toLong(),
        averageBitrate = averageBitrate.takeUnless { it < 0 }?.toLong(),
        approxDurationMs = durationMs,
        // Keep the extractor's complete URL, including n, cpn, pot, range and signature fields.
        url = content
    )

/** Resolves playable audio URLs when InnerTube returns signed formats or an obsolete client error. */
internal object NewPipePlayer {
    init {
        NewPipe.init(NewPipeDownloader)
    }

    fun player(videoId: String): PlayerResponse {
        val extractor = ServiceList.YouTube.getStreamExtractor(
            "https://www.youtube.com/watch?v=$videoId"
        )
        extractor.fetchPage()

        val formats = extractor.audioStreams
            .filter(AudioStream::isUrl)
            .map { stream ->
                stream.toAdaptiveFormat(extractor.length.takeIf { it > 0 }?.times(1000))
            }
            .sortedBy { it.averageBitrate ?: it.bitrate ?: 0 }

        check(formats.isNotEmpty()) { "No playable audio stream found for $videoId" }

        return PlayerResponse(
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK"),
            playerConfig = null,
            streamingData = PlayerResponse.StreamingData(adaptiveFormats = formats),
            videoDetails = PlayerResponse.VideoDetails(videoId = videoId)
        )
    }
}
