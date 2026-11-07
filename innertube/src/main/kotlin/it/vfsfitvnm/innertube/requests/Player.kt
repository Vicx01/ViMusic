package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.player(body: PlayerBody): Result<PlayerResponse>? =
    runCatchingNonCancellable {
        // Always use the maintained extractor for stream URLs. Direct responses from the legacy
        // Android/iOS clients can start playing but lose authorization on later range requests.
        // NewPipe also handles signature/throttling parameters and SABR-only player responses.
        NewPipePlayer.player(body.videoId)
    }
