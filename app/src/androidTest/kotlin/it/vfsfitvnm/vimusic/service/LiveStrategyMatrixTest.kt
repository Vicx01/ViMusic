package it.vfsfitvnm.vimusic.service

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeHttpException
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.prepareGet
import io.ktor.client.request.parameter
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import it.vfsfitvnm.vimusic.service.innertubex.PlaybackAdapter
import it.vfsfitvnm.vimusic.service.innertubex.WebBotGuardTokenProvider
import it.vfsfitvnm.vimusic.service.innertubex.safeModernFailure
import it.vfsfitvnm.vimusic.service.innertubex.safeStreamClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit live-only public fixtures. Never log media IDs, response text, URLs or tokens. */
@RunWith(AndroidJUnit4::class)
class LiveStrategyMatrixTest {
    private fun enabled() = assumeTrue(InstrumentationRegistry.getArguments().getString("vimusicLive") == "1")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Test(timeout = 900_000) fun publicStrategyMatrix() = runBlocking {
        enabled()
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val source = WebBotGuardTokenProvider(appContext)
        var minted: PoTokenResult? = null
        val tokens = object : TokenProvider {
            override val capabilities = source.capabilities
            override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? =
                source.getPoToken(videoId, visitorData, cookie).also { minted = it }
        }
        val http = AtomicInteger()
        val client = HttpClient(OkHttp) {
            expectSuccess = false
            HttpResponseValidator { validateResponse { response ->
                if (response.call.request.url.encodedPath == "/youtubei/v1/player") http.set(response.status.value)
            } }
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 30_000; socketTimeoutMillis = 60_000 }
        }
        val adapter = PlaybackAdapter(client, tokens)
        val mediaClient = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .callTimeout(20, TimeUnit.SECONDS).build()
        // Independent anonymous attempts; forcing a profile has a one-player-request budget.
        val fixtures = listOf(
            "control" to "dQw4w9WgXcQ", "quickpicks" to "J7p4bzqLvCw",
            "pop" to "9bZkp7q19f0", "classical-long" to "rOjHhS5MtvA",
        )
        val profiles = listOf("VISIONOS_0_1", "WEB_REMIX", "WEB_EMBEDDED_PLAYER")
        var usableRanges = 0
        try {
            for ((label, video) in fixtures) for (profile in profiles) {
                http.set(0)
                minted = null
                try {
                    val stream = adapter.resolve(video, profile)
                    val token = minted
                    val tokenState = if (token == null) "not-observed" else "available"
                    val attached = if (token == null) "unknown" else
                        (Uri.parse(stream.url).getQueryParameter("pot") == token.streamingDataToken).toString()
                    val uri = Uri.parse(stream.url)
                    var mediaStatus = 0
                    var mediaReadable = false
                    if (uri.scheme == "https" && uri.host?.endsWith(".googlevideo.com") == true &&
                        stream.headers.keys.none { it.equals("Range", ignoreCase = true) }) {
                        val request = Request.Builder().url(stream.url).header("Range", "bytes=0-65535")
                        stream.headers.forEach { (key, value) -> request.header(key, value) }
                        mediaClient.newCall(request.build()).execute().use { response ->
                            mediaStatus = response.code
                            mediaReadable = (response.code == 200 || response.code == 206) && response.body?.source()?.request(1) == true
                        }
                    }
                    if (mediaReadable) usableRanges++
                    val expiry = stream.expiresAtMs?.let { ((it - System.currentTimeMillis()) / 1000).coerceIn(-1, 86400) } ?: -1
                    val format = when {
                        stream.mimeType?.startsWith("audio/webm") == true -> "webm"
                        stream.mimeType?.startsWith("audio/mp4") == true -> "mp4"
                        else -> "other"
                    }
                    Log.i("ViMusicPlayback", "LIVE_MATRIX fixture=$label profile=$profile result=resolved playerHttp=${http.get()} client=${safeStreamClient(stream.clientName)} itag=${stream.itag} format=$format expirySeconds=$expiry token=$tokenState gvsAttached=$attached mediaHttp=$mediaStatus mediaReadable=$mediaReadable")
                } catch (cancelled: CancellationException) {
                    // An extractor-owned timeout is an individual matrix result; external
                    // cancellation must still stop the whole probe.
                    currentCoroutineContext().ensureActive()
                    Log.i("ViMusicPlayback", "LIVE_MATRIX fixture=$label profile=$profile result=failed playerHttp=${http.get()} reason=timeout")
                } catch (error: Exception) {
                    val tokenState = if (minted == null) "not-observed" else "available"
                    Log.i("ViMusicPlayback", "LIVE_MATRIX fixture=$label profile=$profile result=failed playerHttp=${http.get()} token=$tokenState ${safeModernFailure(error)}")
                } finally {
                    minted = null
                }
            }
        } finally {
            try { adapter.close() } finally { try { source.close() } finally { client.close() } }
        }
        assertTrue("No public fixture returned an initial media range", usableRanges > 0)
    }


    /** Direct untokenized requests reveal status/reason categories; they are not extraction or playback. */
    @Test(timeout = 300_000) fun playerResponseReasons() = runBlocking {
        enabled()
        val client = HttpClient(OkHttp) {
            expectSuccess = false
            followRedirects = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 15_000 }
        }
        val tube = InnerTube(client)
        try {
            val visitor = try { tube.fetchFreshVisitorData() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            val fixtures = listOf(
                "control-r1" to "dQw4w9WgXcQ", "control-r2" to "dQw4w9WgXcQ", "control-r3" to "dQw4w9WgXcQ",
                "quickpicks" to "J7p4bzqLvCw", "pop" to "9bZkp7q19f0", "classical-long" to "rOjHhS5MtvA",
            )
            val profiles = listOf("VISIONOS_0_1" to YouTubeClient.VISIONOS_0_1, "WEB_REMIX" to YouTubeClient.WEB_REMIX,
                "WEB_EMBEDDED_PLAYER" to YouTubeClient.WEB_EMBEDDED_PLAYER)
            for ((label, video) in fixtures) {
                val watchHttp = try {
                    client.prepareGet("https://www.youtube.com/watch") {
                        parameter("v", video)
                        header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    }.execute { response -> response.status.value }
                } catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive(); 0 }
                catch (_: Exception) { 0 }
                Log.i("ViMusicPlayback", "LIVE_WATCH fixture=$label http=$watchHttp")
                for ((profile, ytClient) in profiles) {
                var http = 0
                var status = "unknown"
                var reason = "unknown"
                try {
                    val response = tube.player(ytClient, video, requestVisitorData = visitor)
                    http = response.status.value
                    val text = response.bodyAsText()
                    if (text.length <= 1_000_000) {
                        val playability = json.parseToJsonElement(text).jsonObject["playabilityStatus"]?.jsonObject
                        val value = playability?.get("status")?.jsonPrimitive?.content
                        if (value in setOf("OK", "ERROR", "LOGIN_REQUIRED", "UNPLAYABLE", "VIDEO_UNAVAILABLE", "LIVE_STREAM_OFFLINE", "AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED")) status = value!!
                        val rawReason = playability?.get("reason")?.jsonPrimitive?.content.orEmpty().lowercase()
                        reason = when {
                            rawReason.isBlank() -> "none"
                            "not a bot" in rawReason || "unusual traffic" in rawReason -> "bot-check"
                            listOf("confirm your age", "age-restricted", "age restricted", "age verification").any { it in rawReason } -> "age-check"
                            "country" in rawReason || "region" in rawReason -> "region"
                            "sign in" in rawReason || "login" in rawReason -> "sign-in"
                            "private" in rawReason -> "private"
                            "unavailable" in rawReason || "not available" in rawReason || "removed" in rawReason -> "unavailable"
                            else -> "other"
                        }
                    }
                } catch (cancelled: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    reason = "timeout"
                } catch (failure: InnerTubeHttpException) { http = failure.status.value; reason = "http-error" }
                catch (_: Exception) { reason = "network-or-decode-error" }
                Log.i("ViMusicPlayback", "LIVE_DIRECT fixture=$label profile=$profile http=$http status=$status reason=$reason visitor=${!visitor.isNullOrBlank()} token=absent")
                }
            }
        } finally { try { tube.close() } finally { client.close() } }
    }


    /** Compare the ordinary automatic path with an explicitly visitor-primed session. */
    @Test(timeout = 180_000) fun compareVisitorBootstrap() = runBlocking {
        enabled()
        for (prime in listOf(false, true)) {
            val provider = WebBotGuardTokenProvider(InstrumentationRegistry.getInstrumentation().targetContext)
            val client = HttpClient(OkHttp) {
                expectSuccess = false
                install(ContentNegotiation) { json(json) }
                install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 30_000; socketTimeoutMillis = 60_000 }
            }
            val adapter = PlaybackAdapter(client, provider)
            var visitor = false
            try {
                if (prime) visitor = adapter.probeVisitorBootstrap()
                val stream = adapter.resolve("dQw4w9WgXcQ")
                var mediaHttp = 0
                var readable = false
                val uri = Uri.parse(stream.url)
                if (uri.scheme == "https" && uri.host?.endsWith(".googlevideo.com") == true &&
                    stream.headers.keys.none { it.equals("Range", ignoreCase = true) }) {
                    val request = Request.Builder().url(stream.url).header("Range", "bytes=0-65535")
                    stream.headers.forEach { (key, value) -> request.header(key, value) }
                    val media = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                        .callTimeout(20, TimeUnit.SECONDS).build()
                    media.newCall(request.build()).execute().use { response ->
                        mediaHttp = response.code
                        readable = (response.code == 200 || response.code == 206) && response.body?.source()?.request(1) == true
                    }
                }
                Log.i("ViMusicPlayback", "LIVE_BOOTSTRAP primed=$prime visitor=$visitor result=resolved client=${safeStreamClient(stream.clientName)} itag=${stream.itag} mediaHttp=$mediaHttp readable=$readable")
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                Log.i("ViMusicPlayback", "LIVE_BOOTSTRAP primed=$prime visitor=$visitor result=failed reason=timeout")
            } catch (error: Exception) {
                Log.i("ViMusicPlayback", "LIVE_BOOTSTRAP primed=$prime visitor=$visitor result=failed ${safeModernFailure(error)}")
            } finally { try { adapter.close() } finally { try { provider.close() } finally { client.close() } } }
        }
    }

    @Test(timeout = 120_000) fun realBotGuardAndPlayerAttachment() = runBlocking {
        enabled()
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val tokens = WebBotGuardTokenProvider(appContext)
        var expected: String? = null
        var attached = false
        var bodyKind = "unobserved"
        val client = HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
            install("VerifyTokenAttachment") {
                sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                    if (context.url.encodedPath == "/youtubei/v1/player" && expected != null) {
                        val text = (subject as? TextContent)?.text
                        bodyKind = if (text != null) "text" else "other"
                        val actual = runCatching { text?.let { json.parseToJsonElement(it).jsonObject["serviceIntegrityDimensions"]?.jsonObject?.get("poToken")?.jsonPrimitive?.content } }.getOrNull()
                        attached = actual != null && actual == expected
                    }
                }
            }
        }
        val tube = InnerTube(client)
        var visitor = false
        var player = false
        var gvs = false
        var http = 0
        var status = "unknown"
        try {
            val visitorData = tube.fetchFreshVisitorData()
            visitor = !visitorData.isNullOrBlank()
            if (visitorData != null && visitorData.isNotBlank()) {
                val token = tokens.getPoToken("dQw4w9WgXcQ", visitorData)
                player = !token?.playerRequestToken.isNullOrBlank()
                gvs = !token?.streamingDataToken.isNullOrBlank()
                if (token != null && player) {
                    expected = token.playerRequestToken
                    try {
                        val response = tube.player(YouTubeClient.WEB_REMIX, "dQw4w9WgXcQ", poToken = token.playerRequestToken, requestVisitorData = visitorData)
                        http = response.status.value
                        val text = response.bodyAsText()
                        if (text.length <= 1_000_000) {
                            val candidate = json.parseToJsonElement(text).jsonObject["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
                            if (candidate in setOf("OK", "ERROR", "LOGIN_REQUIRED", "UNPLAYABLE", "VIDEO_UNAVAILABLE", "LIVE_STREAM_OFFLINE")) status = candidate!!
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: InnerTubeHttpException) { http = failure.status.value }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { status = "network-or-decode-error" }
        finally {
            expected = null
            try { tube.close() } finally { try { tokens.close() } finally { client.close() } }
        }
        Log.i("ViMusicPlayback", "LIVE_TOKEN visitor=$visitor player=$player gvs=$gvs playerAttached=$attached body=$bodyKind http=$http status=$status")
        assertTrue("Real BotGuard mint and player-request attachment were not verified", visitor && player && gvs && attached)
    }
}
