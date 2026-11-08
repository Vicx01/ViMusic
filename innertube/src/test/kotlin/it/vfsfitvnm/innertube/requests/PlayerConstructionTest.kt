package it.vfsfitvnm.innertube.requests

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.configureInnertubeClient
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PlayerConstructionTest {
    @Test
    fun productionPlayerRequestHasAuditedIdentityBodyAndHeaders() = runBlocking {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"fixture"}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) { configureInnertubeClient() }
        try {
            val result = Innertube.player(client, PlayerBody(videoId = "fixture"))
            assertTrue(result?.isSuccess == true)
            val request = checkNotNull(captured)
            assertEquals("POST", request.method.value)
            assertEquals("https", request.url.protocol.name)
            assertEquals("music.youtube.com", request.url.host)
            assertEquals("/youtubei/v1/player", request.url.encodedPath)
            assertEquals("false", request.url.parameters["prettyPrint"])
            assertFalse(request.url.parameters.contains("key"))
            assertTrue(request.headers.contains("X-Goog-Api-Key"))
            assertFalse(request.headers.contains("X-YouTube-Client-Name"))
            assertFalse(request.headers.contains("X-YouTube-Client-Version"))
            assertTrue(request.headers[HttpHeaders.UserAgent]?.contains("Chromium/70.0.3538.77") == true)
            assertTrue(request.headers.contains("X-Goog-FieldMask"))
            val body = request.body as OutgoingContent.ByteArrayContent
            val root = Json.parseToJsonElement(body.bytes().decodeToString()).jsonObject
            assertEquals(setOf("context", "videoId"), root.keys)
            assertEquals("fixture", root.getValue("videoId").jsonPrimitive.content)
            val context = root.getValue("context").jsonObject
            assertEquals(setOf("client"), context.keys)
            val fields = context.getValue("client").jsonObject
            assertEquals(setOf("clientName", "clientVersion", "platform", "hl", "visitorData", "androidSdkVersion", "userAgent"), fields.keys)
            assertEquals("ANDROID_MUSIC", fields.getValue("clientName").jsonPrimitive.content)
            assertEquals("5.28.1", fields.getValue("clientVersion").jsonPrimitive.content)
            assertEquals("MOBILE", fields.getValue("platform").jsonPrimitive.content)
            assertEquals("30", fields.getValue("androidSdkVersion").jsonPrimitive.content)
            assertTrue(fields.getValue("userAgent").jsonPrimitive.content.contains("youtube.music/5.28.1"))
            assertTrue(fields.getValue("visitorData").jsonPrimitive.content.isNotEmpty())
            assertEquals("WEB_REMIX", SearchBody(query = "fixture", params = "").context.client.clientName)
        } finally { client.close() }
    }
    @Test
    fun searchStillUsesWebRemixAndTheSharedMusicHost() = runBlocking {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            captured = request
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureInnertubeClient() }
        try {
            client.post(Innertube.search) { setBody(SearchBody(query = "fixture", params = "")) }
            val request = checkNotNull(captured)
            assertEquals("music.youtube.com", request.url.host)
            assertEquals("/youtubei/v1/search", request.url.encodedPath)
            assertTrue(request.headers.contains("X-Goog-Api-Key"))
            val body = request.body as OutgoingContent.ByteArrayContent
            val root = Json.parseToJsonElement(body.bytes().decodeToString()).jsonObject
            val clientFields = root.getValue("context").jsonObject.getValue("client").jsonObject
            assertEquals("WEB_REMIX", clientFields.getValue("clientName").jsonPrimitive.content)
            assertEquals("1.20220918", clientFields.getValue("clientVersion").jsonPrimitive.content)
        } finally { client.close() }
    }

}
