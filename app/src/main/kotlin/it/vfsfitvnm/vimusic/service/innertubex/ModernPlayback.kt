package it.vfsfitvnm.vimusic.service.innertubex

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Owned by the playback service, created only when the experimental provider is selected. */
internal class ModernPlayback(context: Context) {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
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

    suspend fun resolve(videoId: String) = adapter.resolve(videoId)
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
