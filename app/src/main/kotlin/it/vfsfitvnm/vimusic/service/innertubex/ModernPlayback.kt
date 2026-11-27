package it.vfsfitvnm.vimusic.service.innertubex

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.request.HttpSendPipeline
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Log
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Owned by the playback service, created only when the experimental provider is selected. */
internal class ModernPlayback(context: Context) {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        HttpResponseValidator {
            validateResponse { response ->
                val request = response.call.request
                val details = safeHttpObservation(
                    request.url.host, request.url.encodedPath, response.status.value,
                    request.headers.contains("X-Goog-Api-Key"), request.url.parameters.contains("key"),
                    request.headers["X-YouTube-Client-Name"], request.headers["X-YouTube-Client-Version"],
                    request.headers.contains("X-Goog-Visitor-Id"),
                )
                Log.i("ViMusicPlayback", "STREAM_HTTP $details")
            }
        }
        install("SafePlayerRequestObservation") {
            sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                if (context.url.encodedPath == "/youtubei/v1/player" &&
                    context.url.host in setOf("music.youtube.com", "www.youtube.com", "youtubei.googleapis.com")) {
                    val text = (subject as? TextContent)?.text
                    val root = text?.takeIf { it.length <= 200_000 }?.let {
                        runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
                    }
                    val tokenPresent = runCatching {
                        !root?.get("serviceIntegrityDimensions")?.jsonObject?.get("poToken")?.jsonPrimitive?.content.isNullOrBlank()
                    }.getOrDefault(false)
                    val client = context.headers["X-YouTube-Client-Name"]?.takeIf { it.matches(Regex("[0-9]{1,4}")) } ?: "none"
                    val inspected = root != null
                    Log.i("ViMusicPlayback", "STREAM_REQUEST client=$client inspected=$inspected playerToken=$tokenPresent")
                }
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }
    private val tokens = WebBotGuardTokenProvider(context)
    private val adapter = PlaybackAdapter(client, tokens)

    suspend fun resolve(videoId: String): StreamSnapshot = adapter.resolve(videoId).also { stream ->
        val ttl = stream.expiresAtMs?.let { ((it - System.currentTimeMillis()) / 1000).coerceIn(-1, 86400) } ?: -1
        val format = when {
            stream.mimeType?.startsWith("audio/webm") == true -> "webm"
            stream.mimeType?.startsWith("audio/mp4") == true -> "mp4"
            else -> "other"
        }
        Log.i("ViMusicPlayback", "STREAM_SELECTED client=${safeStreamClient(stream.clientName)} itag=${stream.itag.coerceIn(0, 10000)} format=$format expirySeconds=$ttl")
    }
    suspend fun refreshAfterRejection() {
        tokens.invalidateAttestation()
        adapter.refreshAfterStreamRejection()
    }
    suspend fun close() {
        try { adapter.close() } finally {
            try { tokens.close() } finally { client.close() }
        }
    }
}
