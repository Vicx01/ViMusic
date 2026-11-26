package it.vfsfitvnm.vimusic.service

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeHttpException
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Three direct requests differ only in where the existing public web API key is supplied. */
@RunWith(AndroidJUnit4::class)
class LiveKeyPlacementTest {
    @Test(timeout = 180_000) fun compareVisionOsKeyPlacement() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vimusicLive") == "1")
        // The same public web-client key already used by the legacy provider. Never log it.
        val publicKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        var incompleteObservations = 0
        for (placement in listOf("none", "header", "query")) {
            val observed = AtomicInteger()
            val mismatched = AtomicInteger()
            val client = HttpClient(OkHttp) {
                expectSuccess = false
                HttpResponseValidator {
                    validateResponse { response ->
                        val request = response.call.request
                        if (request.url.host == "music.youtube.com" && request.url.encodedPath == "/youtubei/v1/player") {
                            val headers = request.headers.getAll("X-Goog-Api-Key")
                            val query = request.url.parameters.getAll("key")
                            val matches = when (placement) {
                                "header" -> headers == listOf(publicKey) && query == null
                                "query" -> headers == null && query == listOf(publicKey)
                                else -> headers == null && query == null
                            }
                            observed.incrementAndGet()
                            if (!matches) mismatched.incrementAndGet()
                        }
                    }
                }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }) }
                install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
                install("ControlledKeyPlacement") {
                    requestPipeline.intercept(HttpRequestPipeline.State) {
                        if (context.url.host == "music.youtube.com" && context.url.encodedPath == "/youtubei/v1/player") {
                            when (placement) {
                                "header" -> context.headers.append("X-Goog-Api-Key", publicKey)
                                "query" -> context.url.parameters.append("key", publicKey)
                            }
                        }
                    }
                }
            }
            val tube = InnerTube(client)
            var http = 0
            var status = "unknown"
            var audio = "unknown"
            try {
                val response = tube.player(YouTubeClient.VISIONOS_0_1, "dQw4w9WgXcQ")
                http = response.status.value
                val text = response.bodyAsText()
                if (text.length <= 1_000_000) {
                    val body = Json.parseToJsonElement(text).jsonObject
                    val candidate = body["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
                    if (candidate in setOf("OK", "ERROR", "LOGIN_REQUIRED", "UNPLAYABLE", "VIDEO_UNAVAILABLE", "LIVE_STREAM_OFFLINE")) status = candidate!!
                    audio = if (body["streamingData"]?.jsonObject?.get("adaptiveFormats") != null) "present" else "absent"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: InnerTubeHttpException) {
                http = failure.status.value
            } catch (_: Exception) {
                // A fixed category, not the exception message or response body.
                status = "network-or-decode-error"
            } finally {
                tube.close()
                client.close()
            }
            val verified = observed.get() > 0 && mismatched.get() == 0
            if (http == 0 || !verified) incompleteObservations++
            Log.i("ViMusicPlayback", "LIVE_KEY placement=$placement host=music.youtube.com client=VISIONOS version=0.1 http=$http status=$status adaptive=$audio verified=$verified")
        }
        org.junit.Assert.assertEquals("At least one request had no response or unverified key placement", 0, incompleteObservations)
    }
}
