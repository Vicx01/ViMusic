package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.userAgent
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.coroutines.CancellationException

private const val playerResponseMask =
    "playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId"

private suspend fun Innertube.requestPlayer(
    body: PlayerBody,
    context: Context
): PlayerResponse = client.post(player) {
    setBody(body.copy(context = context))
    context.client.userAgent?.let(::userAgent)
    mask(playerResponseMask)
}.body()

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    // Direct InnerTube responses preserve loudness metadata and avoid the more expensive extractor.
    // Try iOS as well because Android clients can return signature-only formats.
    for (context in listOf(Context.DefaultAndroid, Context.DefaultIos)) {
        val result = runCatchingNonCancellable {
            requestPlayer(body, context)
        } ?: throw CancellationException()

        result.getOrNull()
            ?.takeIf {
                it.playabilityStatus?.status == "OK" &&
                    it.videoDetails?.videoId == body.videoId &&
                    it.streamingData?.highestQualityFormat != null
            }
            ?.let { return@runCatchingNonCancellable it }
    }

    // NewPipe keeps YouTube client and signature/throttling decipher logic up to date. Unlike the
    // old public Piped instance, this fallback does not depend on a third-party server being up.
    NewPipePlayer.player(body.videoId)
}
