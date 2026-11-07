package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.utils.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream

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
                PlayerResponse.StreamingData.AdaptiveFormat(
                    itag = stream.itag,
                    mimeType = stream.format?.mimeType ?: "audio/mp4",
                    bitrate = stream.bitrate.takeUnless { it < 0 }?.toLong(),
                    averageBitrate = stream.averageBitrate.takeUnless { it < 0 }?.toLong(),
                    approxDurationMs = extractor.length.takeIf { it > 0 }?.times(1000),
                    url = stream.content
                )
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
