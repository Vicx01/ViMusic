package it.vfsfitvnm.vimusic.service

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
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
    val summary = when {
        causes.any { it is UnresolvedAddressException || it is UnknownHostException } -> "A network error has occurred"
        causes.any { it is PlayableFormatNotFoundException } -> "Couldn't find a playable audio format"
        causes.any { it is UnplayableException } -> "The original video source is unplayable"
        causes.any { it is LoginRequiredException } -> "This song cannot be played due to server restrictions"
        causes.any { it is VideoIdMismatchException } -> "The returned video id doesn't match the requested one"
        else -> "Playback failed"
    }
    val status = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()?.responseCode
    return buildString {
        append(summary)
        append("\n").append(error.errorCodeName).append(" (").append(error.errorCode).append(")")
        if (status != null) append("\nHTTP ").append(status)
        append("\nCause: ").append(causes.joinToString(" → ") { it.javaClass.simpleName })
    }
}
