package it.vfsfitvnm.vimusic.service

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

private val Throwable.causeChain: Sequence<Throwable>
    get() = generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }

internal val PlaybackException.userMessage: String
    get() {
        causeChain.forEach { cause ->
            when (cause) {
                is HttpDataSource.InvalidResponseCodeException -> {
                    val status = listOfNotNull(
                        cause.responseCode.toString(),
                        cause.responseMessage?.takeIf(String::isNotBlank)
                    ).joinToString(" ")
                    val body = cause.responseBody
                        .toString(Charsets.UTF_8)
                        .trim()
                        .take(240)

                    return buildString {
                        append("Stream request failed: HTTP ")
                        append(status)
                        if (body.isNotEmpty()) {
                            append(" — ")
                            append(body)
                        }
                    }
                }
                is UnresolvedAddressException, is UnknownHostException ->
                    return "A network error has occurred"
                is PlayableFormatNotFoundException ->
                    return "Couldn't find a playable audio format"
                is UnplayableException ->
                    return "The original video source of this song has been deleted"
                is LoginRequiredException ->
                    return "This song cannot be played due to server restrictions"
                is VideoIdMismatchException ->
                    return "The returned video id doesn't match the requested one"
            }
        }

        val detail = causeChain
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .lastOrNull()
            ?: "error code $errorCode"
        return "Playback failed: $detail"
    }

internal val PlaybackException.diagnosticMessage: String
    get() {
        val httpError = causeChain
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?: return userMessage

        return buildString {
            append(userMessage)
            append(" | request=")
            append(httpError.dataSpec.uri.host)
            append(httpError.dataSpec.uri.path)
            append(" position=")
            append(httpError.dataSpec.absoluteStreamPosition)
            append(" length=")
            append(httpError.dataSpec.length)
            append(" headers=")
            append(httpError.headerFields)
        }
    }
