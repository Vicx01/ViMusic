package io.ktor.client.plugins.compression

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotliEncoderTest {
    @Test
    fun brotliResponseIsDecodedAndAdvertised() = runBlocking {
        // Node zlib Brotli-compressed UTF-8 text; no live network or user data.
        val compressed = Base64.getDecoder().decode("Cw+AVmlNdXNpYyBCcm90bGkgcmVzcG9uc2UgZml4dHVyZQM=")
        var advertised = false
        val engine = MockEngine { request ->
            advertised = request.headers[HttpHeaders.AcceptEncoding]?.contains("br") == true
            respond(ByteReadChannel(compressed), HttpStatusCode.OK, headersOf(HttpHeaders.ContentEncoding, "br"))
        }
        val client = HttpClient(engine) { install(ContentEncoding) { brotli() } }
        try {
            assertEquals("ViMusic Brotli response fixture", client.get("https://fixture.invalid/").bodyAsText())
            assertTrue("Brotli must remain advertised to the legacy provider", advertised)
        } finally {
            client.close()
        }
    }
}
