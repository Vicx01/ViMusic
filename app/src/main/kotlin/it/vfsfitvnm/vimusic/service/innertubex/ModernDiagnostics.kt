package it.vfsfitvnm.vimusic.service.innertubex

import com.metrolist.innertubex.extraction.StreamResolveException
import io.ktor.client.plugins.ResponseException

/** Restrict diagnostics to fixed categories and library-defined client names. */
private val knownClients = setOf(
    "WEB_REMIX", "WEB_CREATOR", "TVHTML5", "VISIONOS", "ANDROID_VR", "TVHTML5_SIMPLY",
    "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "WEB", "MWEB", "WEB_EMBEDDED_PLAYER", "IOS", "WEB_KIDS",
    "ANDROID_MUSIC", "ANDROID_CREATOR", "ANDROID", "IOS_MUSIC", "ANDROID_KIDS", "ANDROID_PRODUCER",
    "MEDIA_CONNECT_FRONTEND",
)
internal fun safeStreamClient(value: String): String = value.takeIf { it in knownClients } ?: "unknown"

internal fun safeModernFailure(error: Exception): String {
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    val causes = mutableListOf<Throwable>()
    var next: Throwable? = error
    while (next != null && causes.size < 12 && seen.add(next)) {
        causes.add(next)
        next = next.cause
    }
    val failure = causes.filterIsInstance<StreamResolveException>().firstOrNull()
    val response = causes.filterIsInstance<ResponseException>().firstOrNull()
    val status = response?.response?.status?.value?.takeIf { it in 100..599 }?.toString() ?: "none"
    val host = response?.response?.call?.request?.url?.host?.takeIf {
        it in setOf("www.youtube.com", "music.youtube.com", "youtubei.googleapis.com", "youtube.com")
    } ?: "other-or-unknown"
    val attempts = failure?.diagnostics?.attempts.orEmpty().take(8).joinToString(",") { attempt ->
        val outcome = when {
            attempt.outcome == "po_token_unavailable" -> "token-unavailable"
            attempt.outcome.startsWith("playability:") -> "not-playable"
            attempt.outcome.startsWith("request:") -> "request-failed"
            attempt.outcome == "playable_response" -> "playable"
            attempt.outcome == "request_budget_exhausted" -> "budget-exhausted"
            attempt.outcome.startsWith("selection:") -> "not-selected"
            else -> "other"
        }
        "${safeStreamClient(attempt.clientName)}:$outcome"
    }
    return "reason=${failure?.reason?.name ?: "other"} http=$status host=$host attempts=${attempts.ifEmpty { "none" }}"
}
