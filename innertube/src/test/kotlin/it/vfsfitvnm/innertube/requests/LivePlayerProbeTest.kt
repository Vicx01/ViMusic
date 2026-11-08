package it.vfsfitvnm.innertube.requests

import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import kotlinx.coroutines.runBlocking
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
        val webVersion = "1.20260707.12.00"
        val web = original.copy(context = Context.DefaultWeb.copy(
            client = Context.DefaultWeb.client.copy(clientVersion = webVersion)
        ))
        data class Probe(val label: String, val path: String, val body: PlayerBody, val webHeaders: Boolean = false)
        val probes = listOf(
            Probe("legacy_music_host", "/youtubei/v1/player", original),
            // Absolute URL bypasses Ktor's default URL query merge: add the same prettyPrint query explicitly below.
            Probe("www_host_only", "https://www.youtube.com/youtubei/v1/player", original),
            // Only the context version changes; the intentionally unchanged UA still contains 5.28.1.
            Probe("music_version_only", "/youtubei/v1/player", newerVersion),
            // Separate cohesive profile control, not a one-variable comparison with the Android baseline.
            Probe("web_remix_control", "/youtubei/v1/player", web, webHeaders = true),
        )
        for (probe in probes) {
            try {
                val response = Innertube.client.post(probe.path) {
                    if (probe.path.startsWith("https://")) parameter("prettyPrint", "false")
                    setBody(probe.body)
                    header("X-Goog-FieldMask", "playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
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
        }
    }

    private fun summarizeSuccess(text: String): String {
        val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (_: IllegalArgumentException) { null }
            ?: return "body=unrecognized"
        val playability = ((root["playabilityStatus"] as? JsonObject)?.get("status") as? JsonPrimitive)?.content
        val status = playability?.takeIf { it in setOf("OK", "LOGIN_REQUIRED", "UNPLAYABLE", "ERROR", "CONTENT_CHECK_REQUIRED", "AGE_CHECK_REQUIRED", "LIVE_STREAM_OFFLINE") } ?: "other_or_absent"
        val formats = (root["streamingData"] as? JsonObject)?.get("adaptiveFormats") as? JsonArray
        val direct = formats?.count { (it as? JsonObject)?.get("url") is JsonPrimitive } ?: 0
        return "playability=$status|adaptive_formats=${formats?.size ?: 0}|direct_urls=$direct"
    }
}
