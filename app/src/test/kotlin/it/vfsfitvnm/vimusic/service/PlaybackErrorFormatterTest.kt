package it.vfsfitvnm.vimusic.service

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackErrorFormatterTest {
    private fun playbackError(cause: Throwable) = PlaybackException(
        "Playback failed",
        cause,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    )

    @Test
    fun `shows nested HTTP status and response body`() {
        val httpError = HttpDataSource.InvalidResponseCodeException(
            403,
            "Forbidden",
            null,
            emptyMap(),
            DataSpec.Builder().setUri("https://example.com/audio").build(),
            "PO token required".toByteArray()
        )
        val wrapped = playbackError(Exception("Range request failed", httpError))

        assertEquals(
            "Stream request failed: HTTP 403 Forbidden — PO token required",
            wrapped.userMessage
        )
    }

    @Test
    fun `does not hide non HTTP failures behind unknown error`() {
        val error = playbackExceptionWithMessage("Connection timed out")

        assertEquals("Playback failed: Connection timed out", error.userMessage)
    }

    @Test
    fun `diagnostics include failing range but not signed query parameters`() {
        val httpError = HttpDataSource.InvalidResponseCodeException(
            410,
            "Gone",
            null,
            mapOf("Content-Type" to listOf("text/plain")),
            DataSpec.Builder()
                .setUri("https://rr1---sn-example.googlevideo.com/videoplayback?sig=secret")
                .setPosition(512 * 1024L)
                .setLength(512 * 1024L)
                .build(),
            "Expired".toByteArray()
        )
        val error = playbackError(httpError)

        val diagnostic = error.diagnosticMessage
        assertTrue(diagnostic.contains("HTTP 410 Gone"))
        assertTrue(diagnostic.contains("length="))
        assertFalse(diagnostic.contains("sig=secret"))
    }

    private fun playbackExceptionWithMessage(message: String) = PlaybackException(
        message,
        null,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    )
}
