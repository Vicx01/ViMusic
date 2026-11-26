package it.vfsfitvnm.vimusic.service.innertubex

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** Production artifact request construction, with one controlled input change and no network. */
class InnerTubeXRequestTest {
    @Test fun publishedAndroidVrRequestKeepsIdentityBodyHostAndKeyHandling() = runBlocking {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }
        val tube = InnerTube(client)
        try {
            tube.player(YouTubeClient.ANDROID_VR_1_43_32, "fixture-a")
            tube.player(YouTubeClient.ANDROID_VR_1_43_32, "fixture-b")
            assertEquals(2, requests.size)
            val bodies = requests.map { request ->
                assertEquals("music.youtube.com", request.url.host)
                assertEquals("28", request.headers["X-YouTube-Client-Name"])
                assertEquals(YouTubeClient.ANDROID_VR_1_43_32.clientVersion, request.headers["X-YouTube-Client-Version"])
                assertEquals("/youtubei/v1/player", request.url.encodedPath)
                assertNull(request.headers["X-Goog-Api-Key"])
                assertNull(request.url.parameters["key"])
                val text = when (val content = request.body) {
                    is TextContent -> content.text
                    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                    else -> error("Unexpected fixture body type")
                }
                Json.parseToJsonElement(text).jsonObject
            }
            assertEquals("fixture-a", bodies[0]["videoId"]?.jsonPrimitive?.content)
            assertEquals("fixture-b", bodies[1]["videoId"]?.jsonPrimitive?.content)
            assertEquals("ANDROID_VR", bodies[0]["context"]?.jsonObject?.get("client")?.jsonObject?.get("clientName")?.jsonPrimitive?.content)
            assertEquals(YouTubeClient.ANDROID_VR_1_43_32.clientVersion,
                bodies[0]["context"]?.jsonObject?.get("client")?.jsonObject?.get("clientVersion")?.jsonPrimitive?.content)
            assertTrue("Only video ID should differ between the controlled request bodies",
                bodies[0].filterKeys { it != "videoId" } == bodies[1].filterKeys { it != "videoId" })
        } finally {
            tube.close()
            client.close()
        }
    }
}
