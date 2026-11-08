package it.vfsfitvnm.innertube.requests

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

/** The known request that failed, without video IDs or URLs. */
enum class PlayerRequestStage(val description: String, internal val expectedHost: String) {
    ANDROID_PLAYER("InnerTube player (Android client)", "music.youtube.com"),
    EMBEDDED_PLAYER("InnerTube player (embedded client)", "music.youtube.com"),
    PIPED_STREAMS("Piped stream resolution", "watchapi.whatever.social")
}

/** IOException makes the failure compatible with Media3's loader; the original exception is retained. */
class PlayerRequestException(
    val stage: PlayerRequestStage,
    val httpStatus: Int,
    val responseSummary: String,
    val finalOrigin: String,
    val apiKeyHeaderPresent: Boolean,
    cause: Throwable
) : IOException("${stage.description} failed with HTTP $httpStatus", cause)

private val safeStatuses = setOf(
    "PERMISSION_DENIED", "INVALID_ARGUMENT", "UNAUTHENTICATED", "RESOURCE_EXHAUSTED",
    "NOT_FOUND", "FAILED_PRECONDITION", "UNAVAILABLE", "INTERNAL", "ABORTED"
)
private val safeReasons = setOf(
    "forbidden", "rateLimitExceeded", "quotaExceeded", "accessNotConfigured",
    "notFound", "unauthorized", "badRequest", "invalid", "permissionDenied"
)

/** Only allowlisted structured fields are returned; arbitrary server text can contain secrets. */
internal fun summarizePlayerErrorBody(text: String): String {
    if (text.length > 16_384) return "omitted (body too large)"
    val root = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return "omitted (not recognized JSON)"
    val error = root["error"] as? JsonObject ?: return "omitted (unrecognized JSON)"
    val fields = mutableListOf<String>()
    (error["code"] as? JsonPrimitive)?.content?.toIntOrNull()
        ?.takeIf { it in 100..599 }?.let { fields += "code=$it" }
    (error["status"] as? JsonPrimitive)?.content?.takeIf { it in safeStatuses }
        ?.let { fields += "status=$it" }
    ((error["errors"] as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.get("reason")?.let { it as? JsonPrimitive }?.content?.takeIf { it in safeReasons }
        ?.let { fields += "reason=$it" }
    return fields.joinToString(", ").ifEmpty { "omitted (no allowlisted fields)" }
}

internal suspend fun <T> playerHttpRequest(stage: PlayerRequestStage, block: suspend () -> T): T {
    try {
        return block()
    } catch (exception: ResponseException) {
        val response = exception.response
        val summary = try {
            summarizePlayerErrorBody(response.bodyAsText())
        } catch (exceptionWhileReading: Exception) {
            if (exceptionWhileReading is CancellationException) throw exceptionWhileReading
            exception.addSuppressed(exceptionWhileReading)
            "unavailable"
        }
        val request = response.call.request
        val finalOrigin = if (request.url.host.equals(stage.expectedHost, ignoreCase = true)) {
            stage.expectedHost
        } else {
            "other host (redacted)"
        }
        throw PlayerRequestException(
            stage = stage,
            httpStatus = response.status.value,
            responseSummary = summary,
            finalOrigin = finalOrigin,
            apiKeyHeaderPresent = request.headers.contains("X-Goog-Api-Key"),
            cause = exception
        )
    }
}
