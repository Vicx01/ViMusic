package it.vfsfitvnm.vimusic.service

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import it.vfsfitvnm.innertube.requests.PlayerRequestException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Collections
import java.util.IdentityHashMap

/** Reports exception types and HTTP status without exposing signed URLs or response bodies. */
internal fun playbackErrorDetails(error: PlaybackException): String {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null && causes.size < 16 && seen.add(current)) {
        causes.add(current)
        current = current.cause
    }
    val resolverError = causes.filterIsInstance<PlayerRequestException>().firstOrNull()
    val summary = when {
        resolverError != null -> "Could not resolve this song"
        causes.any { it is UnresolvedAddressException || it is UnknownHostException } -> "A network error has occurred"
        causes.any { it is PlayableFormatNotFoundException } -> "Couldn't find a playable audio format"
        causes.any { it is UnplayableException } -> "The original video source is unplayable"
        causes.any { it is LoginRequiredException } -> "This song cannot be played due to server restrictions"
        causes.any { it is VideoIdMismatchException } -> "The returned video id doesn't match the requested one"
        else -> "Playback failed"
    }
    val audioError = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
    return buildString {
        append(summary)
        append("\n").append(error.errorCodeName).append(" (").append(error.errorCode).append(")")
        if (resolverError != null) {
            append("\nRequest: ").append(resolverError.stage.description)
            append("\nHTTP ").append(resolverError.httpStatus)
            append("\nResponse: ").append(resolverError.responseSummary)
            append("\nFinal host: ").append(resolverError.finalOrigin)
            append("; API-key header: ").append(if (resolverError.apiKeyHeaderPresent) "present" else "absent")
        } else if (audioError != null) {
            val spec = audioError.dataSpec
            val ranged = spec.position != 0L || spec.length != C.LENGTH_UNSET.toLong() ||
                spec.httpRequestHeaders.keys.any { it.equals("Range", ignoreCase = true) }
            append("\nAudio request: ").append(if (ranged) "range" else "initial/open-ended")
            append("; position=").append(spec.position).append("; length=").append(spec.length)
            append("\nAudio HTTP ").append(audioError.responseCode)
            append("\n").append(PlaybackNetworkDiagnostics.safeAudioResponseHeaders(audioError.headerFields))
            append("\nResponse body: omitted")
        }
        append("\nCause: ").append(causes.joinToString(" → ") { it.javaClass.simpleName })
    }
}
