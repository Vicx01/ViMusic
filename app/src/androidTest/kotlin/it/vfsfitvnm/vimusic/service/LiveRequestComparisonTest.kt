package it.vfsfitvnm.vimusic.service

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpSendPipeline
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import it.vfsfitvnm.vimusic.service.innertubex.PlaybackAdapter
import it.vfsfitvnm.vimusic.service.innertubex.WebBotGuardTokenProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Compare actual serialized production-extractor requests without emitting any request data. */
@RunWith(AndroidJUnit4::class)
class LiveRequestComparisonTest {
    private data class Observation(
        val context: JsonElement?,
        val parameters: JsonObject,
        val headers: List<String?>,
        val cookie: Boolean,
        val authorization: Boolean,
        val playerToken: Boolean,
    )

    @Test(timeout = 300_000) fun compareSameClientAcrossPublicFixtures() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vimusicLive") == "1")
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = WebBotGuardTokenProvider(appContext)
        val minted = AtomicReference<Pair<String, PoTokenResult>?>(null)
        val tokens = object : TokenProvider {
            override val capabilities = provider.capabilities
            override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? =
                provider.getPoToken(videoId, visitorData, cookie).also { result ->
                    if (result != null) minted.set(videoId to result)
                }
        }
        val snapshots = ConcurrentHashMap<String, Observation>()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
        val client = HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 30_000; socketTimeoutMillis = 60_000 }
            install("ComparePlayerRequests") {
                sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                    if (context.url.encodedPath == "/youtubei/v1/player") {
                        val text = (subject as? TextContent)?.text?.takeIf { it.length <= 200_000 }
                        val body = text?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
                        val video = runCatching { body?.get("videoId")?.jsonPrimitive?.content }.getOrNull()
                        val label = when (video) { "dQw4w9WgXcQ" -> "control"; "J7p4bzqLvCw" -> "quickpicks"; else -> null }
                        val profile = when (context.headers["X-YouTube-Client-Name"]) { "101" -> "VISIONOS_0_1"; "67" -> "WEB_REMIX"; else -> null }
                        if (label != null && profile != null && body != null) {
                            val playerToken = runCatching {
                                !body["serviceIntegrityDimensions"]?.jsonObject?.get("poToken")?.jsonPrimitive?.content.isNullOrBlank()
                            }.getOrDefault(false)
                            val names = listOf("User-Agent", "Origin", "Referer", "X-YouTube-Client-Name", "X-YouTube-Client-Version", "X-Goog-Visitor-Id", "X-Goog-Api-Key")
                            // Visitor values, headers and request bodies remain in memory only.
                            snapshots["$profile:$label"] = Observation(
                                context = body["context"],
                                parameters = JsonObject(body.filterKeys { it !in setOf("videoId", "context", "serviceIntegrityDimensions") }),
                                headers = names.map { context.headers[it] },
                                cookie = context.headers.contains("Cookie"),
                                authorization = context.headers.contains("Authorization"),
                                playerToken = playerToken,
                            )
                        }
                    }
                }
            }
        }
        val adapter = PlaybackAdapter(client, tokens)
        var incomplete = false
        try {
            for ((profile, id) in listOf("VISIONOS_0_1" to "101", "WEB_REMIX" to "67")) {
                for ((label, video) in listOf("control" to "dQw4w9WgXcQ", "quickpicks" to "J7p4bzqLvCw")) {
                    minted.set(null)
                    try {
                        val stream = adapter.resolve(video, profile)
                        val token = minted.get()?.takeIf { it.first == video }?.second
                        val gvs = if (token == null) "unknown" else (Uri.parse(stream.url).getQueryParameter("pot") == token.streamingDataToken).toString()
                        Log.i("ViMusicPlayback", "LIVE_COMPARE_RESULT profile=$profile fixture=$label client=$id result=resolved token=${token != null} gvsAttached=$gvs")
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        Log.i("ViMusicPlayback", "LIVE_COMPARE_RESULT profile=$profile fixture=$label client=$id result=timeout")
                    } catch (_: Exception) {
                        Log.i("ViMusicPlayback", "LIVE_COMPARE_RESULT profile=$profile fixture=$label client=$id result=failed token=${minted.get()?.first == video}")
                    } finally { minted.set(null) }
                }
                val a = snapshots["$profile:control"]
                val b = snapshots["$profile:quickpicks"]
                val observed = a != null && b != null
                Log.i("ViMusicPlayback", "LIVE_COMPARE profile=$profile observed=$observed contextEqual=${observed && a!!.context == b!!.context} parametersEqual=${observed && a!!.parameters == b!!.parameters} headersEqual=${observed && a!!.headers == b!!.headers} controlCookie=${a?.cookie ?: false} fixtureCookie=${b?.cookie ?: false} controlAuth=${a?.authorization ?: false} fixtureAuth=${b?.authorization ?: false} controlPlayerToken=${a?.playerToken ?: false} fixturePlayerToken=${b?.playerToken ?: false}")
                if (!observed) incomplete = true
            }
            assertTrue("Both serialized player requests must be observed for each profile", !incomplete)
        } finally {
            snapshots.clear()
            minted.set(null)
            try { adapter.close() } finally { try { provider.close() } finally { client.close() } }
        }
    }
}
