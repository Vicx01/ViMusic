package it.vfsfitvnm.innertube.requests

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlayerRequestTest {
    private val sensitiveBody = """{"error":{"code":403,"status":"PERMISSION_DENIED","message":"https://secret.invalid/?token=do-not-show","errors":[{"reason":"forbidden"}]}}"""
    private fun player(status: String) = """{"playabilityStatus":{"status":"$status"},"playerConfig":null,"streamingData":{"adaptiveFormats":[]},"videoDetails":{"videoId":"fixture"}}"""

    @Test
    fun eachRequestPhasePreservesKtorCauseAndReportsOnlySafeMetadata() = runBlocking {
        val scenarios = listOf(
            PlayerRequestStage.ANDROID_PLAYER to emptyList(),
            PlayerRequestStage.EMBEDDED_PLAYER to listOf(player("LOGIN_REQUIRED")),
            PlayerRequestStage.PIPED_STREAMS to listOf(player("LOGIN_REQUIRED"), player("OK"))
        )
        for ((stage, successes) in scenarios) {
            val requests = mutableListOf<HttpRequestData>()
            val engine = MockEngine { request ->
                requests += request
                val index = requests.lastIndex
                if (index < successes.size) {
                    respond(successes[index], HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond(sensitiveBody, HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            val client = HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
                defaultRequest {
                    url(scheme = "https", host = "music.youtube.com")
                    headers.append(HttpHeaders.ContentType, "application/json")
                    headers.append("X-Goog-Api-Key", "do-not-show")
                }
            }
            try {
                val error = Innertube.player(client, PlayerBody(videoId = "fixture"))!!.exceptionOrNull()
                assertTrue("stage=$stage exceptionType=${error?.javaClass?.simpleName}", error is PlayerRequestException)
                error as PlayerRequestException
                assertTrue(error.cause is ClientRequestException)
                assertEquals(stage, error.stage)
                assertEquals(403, error.httpStatus)
                assertEquals("code=403, status=PERMISSION_DENIED, reason=forbidden", error.responseSummary)
                assertEquals(stage.expectedHost, error.finalOrigin)
                assertTrue(error.apiKeyHeaderPresent)
                assertEquals(successes.size + 1, requests.size)
                assertEquals(if (stage == PlayerRequestStage.PIPED_STREAMS) "GET" else "POST", requests.last().method.value)
                assertEquals(stage.expectedHost, requests.last().url.host)
                assertFalse(error.message!!.contains("do-not-show"))
                assertFalse(error.responseSummary.contains("secret.invalid"))
            } finally { client.close() }
        }
    }

    @Test
    fun unknownBodiesAndUntrustedFieldsAreNotExposed() {
        assertEquals("omitted (not recognized JSON)", summarizePlayerErrorBody("https://secret.invalid/token"))
        assertEquals("omitted (body too large)", summarizePlayerErrorBody("x".repeat(16_385)))
        assertEquals("omitted (no allowlisted fields)", summarizePlayerErrorBody("""{"error":{"status":"secret","message":"do-not-show","errors":[{"reason":"do-not-show"}]}}"""))
        assertEquals("omitted (unrecognized JSON)", summarizePlayerErrorBody("""{"message":"do-not-show"}"""))
    }

    @Test
    fun networkIoFailureIsNotSwallowedOrReclassified() = runBlocking {
        val failure = IOException("fixture")
        try {
            playerHttpRequest<Unit>(PlayerRequestStage.ANDROID_PLAYER) { throw failure }
            fail("Expected exception")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
    }
}
