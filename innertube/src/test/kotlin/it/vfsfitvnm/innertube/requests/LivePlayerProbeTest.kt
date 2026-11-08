package it.vfsfitvnm.innertube.requests

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in investigation only. Never prints request IDs, body text, headers or URLs. */
class LivePlayerProbeTest {
    @Test
    fun controlledPlayerRequests() = runBlocking {
        assumeTrue(System.getenv("VIMUSIC_LIVE_PLAYER_PROBE") == "1")
        // Public fallback video already referenced by QuickPicks, not a user's listening history.
        val original = PlayerBody(videoId = "J7p4bzqLvCw")
        val newerVersion = original.copy(context = original.context.copy(
            client = original.context.client.copy(clientVersion = "8.39.42")
        ))
        val mobileAgent = "com.google.android.apps.youtube.music/8.39.42 (Linux; U; Android 11) gzip"
        val matchingBodyAgent = newerVersion.copy(context = newerVersion.context.copy(
            client = newerVersion.context.client.copy(userAgent = mobileAgent)
        ))
        val mobileClient = Innertube.client.config { install(UserAgent) { agent = mobileAgent } }
        val queryKeyClient = Innertube.client.config {
            install("ProbeKeyLocation") {
                requestPipeline.intercept(HttpRequestPipeline.State) {
                    val key = context.headers["X-Goog-Api-Key"]
                    if (key != null) {
                        context.headers.remove("X-Goog-Api-Key")
                        context.url.parameters.append("key", key)
                    }
                }
            }
        }
        val webVersion = "1.20260707.12.00"
        val web = original.copy(context = Context.DefaultWeb.copy(
            client = Context.DefaultWeb.client.copy(clientVersion = webVersion)
        ))
        data class Probe(
            val label: String, val path: String, val body: PlayerBody,
            val client: HttpClient = Innertube.client, val webHeaders: Boolean = false,
            val clientNameHeader: Boolean = false, val clientVersionHeader: Boolean = false,
            val includeReason: Boolean = false
        )
        val musicPath = "/youtubei/v1/player"
        val probes = listOf(
            Probe("legacy_music_host", musicPath, original),
            // Absolute URL bypasses Ktor's default query merge; keep prettyPrint below.
            Probe("www_host_only", "https://www.youtube.com/youtubei/v1/player", original),
            Probe("legacy_query_key_only", musicPath, original, client = queryKeyClient),
            // Only context version changes; the deliberately unchanged UA still contains 5.28.1.
            Probe("music_version_only", musicPath, newerVersion),
            Probe("music_version_body_agent", musicPath, matchingBodyAgent),
            Probe("music_version_http_agent", musicPath, matchingBodyAgent, client = mobileClient),
            Probe("music_client_name_header", musicPath, matchingBodyAgent, client = mobileClient, clientNameHeader = true),
            Probe("music_client_both_headers", musicPath, matchingBodyAgent, client = mobileClient, clientNameHeader = true, clientVersionHeader = true),
            Probe("music_reason_control", musicPath, matchingBodyAgent, client = mobileClient, clientNameHeader = true, clientVersionHeader = true, includeReason = true),
            // A second public music video isolates video ID from the corresponding first-track cases.
            Probe("legacy_other_song", musicPath, original.copy(videoId = "dQw4w9WgXcQ")),
            Probe("version_other_song", musicPath, newerVersion.copy(videoId = "dQw4w9WgXcQ")),
            // Separate cohesive profile control, not a one-variable comparison with Android.
            Probe("web_remix_control", musicPath, web, webHeaders = true),
            Probe("web_reason_control", musicPath, web, webHeaders = true, includeReason = true),
        )
        for (probe in probes) {
            try {
                val response = probe.client.post(probe.path) {
                    if (probe.path.startsWith("https://")) parameter("prettyPrint", "false")
                    setBody(probe.body)
                    val mask = "playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId" +
                        if (probe.includeReason) ",playabilityStatus.reason" else ""
                    header("X-Goog-FieldMask", mask)
                    if (probe.clientNameHeader) header("X-YouTube-Client-Name", "21")
                    if (probe.clientVersionHeader) header("X-YouTube-Client-Version", "8.39.42")
                    if (probe.webHeaders) {
                        header("X-YouTube-Client-Name", "67")
                        header("X-YouTube-Client-Version", webVersion)
                        header("Origin", "https://music.youtube.com")
                        header("Referer", "https://music.youtube.com/")
                    }
                }
                val summary = summarizeSuccess(response.bodyAsText())
                println("PROBE|${probe.label}|http=${response.status.value}|$summary")
            } catch (e: ResponseException) {
                val summary = try { summarizePlayerErrorBody(e.response.bodyAsText()) } catch (_: Exception) { "unavailable" }
                println("PROBE|${probe.label}|http=${e.response.status.value}|body=$summary")
            } catch (e: Exception) {
                // The exception message can contain a URL or credentials: report only a bounded class name.
                val type = e.javaClass.simpleName.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,80}")) } ?: "OtherException"
                println("PROBE|${probe.label}|http=none|transport=$type")
            }
            delay(500)
        }
        // These cloned clients share the original engine. No further calls are made after closing.
        mobileClient.close()
        queryKeyClient.close()
    }

    private fun summarizeSuccess(text: String): String {
        val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (_: IllegalArgumentException) { null }
            ?: return "body=unrecognized"
        val playability = ((root["playabilityStatus"] as? JsonObject)?.get("status") as? JsonPrimitive)?.content
        val status = playability?.takeIf { it in setOf("OK", "LOGIN_REQUIRED", "UNPLAYABLE", "ERROR", "CONTENT_CHECK_REQUIRED", "AGE_CHECK_REQUIRED", "LIVE_STREAM_OFFLINE") } ?: "other_or_absent"
        val reason = ((root["playabilityStatus"] as? JsonObject)?.get("reason") as? JsonPrimitive)?.content
        val reasonCategory = when {
            reason == null -> "absent"
            reason.contains("not a bot", ignoreCase = true) -> "bot_check"
            reason.contains("age", ignoreCase = true) -> "age_related"
            else -> "other"
        }
        val formats = (root["streamingData"] as? JsonObject)?.get("adaptiveFormats") as? JsonArray
        val direct = formats?.count { (it as? JsonObject)?.get("url") is JsonPrimitive } ?: 0
        return "playability=$status|reason=$reasonCategory|adaptive_formats=${formats?.size ?: 0}|direct_urls=$direct"
    }
}
