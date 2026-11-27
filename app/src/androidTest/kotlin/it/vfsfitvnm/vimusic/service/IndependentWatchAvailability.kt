package it.vfsfitvnm.vimusic.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Independent anonymous watch-page evidence. A 200, a generic LOGIN_REQUIRED,
 * UNPLAYABLE or a missing/malformed page is never treated as a restriction.
 * The page and reasons are untrusted: neither is logged, returned, or persisted.
 */
internal object IndependentWatchAvailability {
    fun confirmedRestriction(videoId: String): String? {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return null
        return try {
            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .callTimeout(15, TimeUnit.SECONDS).build()
            val request = Request.Builder().url("https://www.youtube.com/watch?v=$videoId")
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return null
                val input = response.body?.byteStream() ?: return null
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() < 2_000_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 2_000_000 - out.size()))
                    if (count < 0) break
                    out.write(buffer, 0, count)
                }
                val page = out.toString(Charsets.UTF_8.name())
                val marker = Regex("(?:var\\s+)?ytInitialPlayerResponse\\s*=\\s*").find(page) ?: return null
                val start = page.indexOf('{', marker.range.last + 1)
                if (start < 0) return null
                val end = objectEnd(page, start) ?: return null
                val body = Json.parseToJsonElement(page.substring(start, end)).jsonObject
                // Avoid mistaking a consent/interstitial or another video's response for this fixture.
                if (body["videoDetails"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content != videoId) return null
                val playability = body["playabilityStatus"]?.jsonObject ?: return null
                val status = playability["status"]?.jsonPrimitive?.content ?: return null
                val reason = playability["reason"]?.jsonPrimitive?.content.orEmpty().lowercase()
                when {
                    status in setOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED") -> "age-restricted"
                    status == "LOGIN_REQUIRED" && (playability["desktopLegacyAgeGateReason"]?.jsonPrimitive?.intOrNull ?: 0) > 0 -> "age-restricted"
                    status in setOf("ERROR", "VIDEO_UNAVAILABLE", "UNPLAYABLE") &&
                        listOf("not available in your country", "unavailable in your country", "blocked in your country").any { it in reason } -> "region-restricted"
                    status in setOf("ERROR", "VIDEO_UNAVAILABLE", "UNPLAYABLE") &&
                        listOf("this video is private", "video has been removed", "video was removed").any { it in reason } -> "private-or-removed"
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // One JSON object, honoring quoted braces and escapes; never return partial data.
    private fun objectEnd(text: String, start: Int): Int? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until minOf(text.length, start + 1_000_000)) {
            val c = text[index]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return index + 1
            }
        }
        return null
    }
}
