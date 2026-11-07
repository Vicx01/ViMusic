package it.vfsfitvnm.vimusic.service

import android.net.Uri
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreamDataSpecTest {
    private val chunkLength = 512 * 1024L
    private val streamUrl = "https://rr1---sn-example.googlevideo.com/videoplayback" +
        "?id=video-id&itag=140&sig=signature&n=decoded&cpn=nonce&pot=proof"

    private fun dataSpec(position: Long) = DataSpec.Builder()
        .setUri("video-id")
        .setKey("video-id")
        .setPosition(position)
        .build()

    @Test
    fun `first and subsequent requests use identical chunk length`() {
        val first = dataSpec(position = 0)
            .withStreamUri(Uri.parse(streamUrl), chunkLength)
        val subsequent = dataSpec(position = chunkLength)
            .withStreamUri(Uri.parse(streamUrl), chunkLength)

        assertEquals(0L, first.position)
        assertEquals(chunkLength, first.length)
        assertEquals(chunkLength, subsequent.position)
        assertEquals(chunkLength, subsequent.length)
        assertEquals(streamUrl, subsequent.uri.toString())
        assertEquals("video-id", subsequent.key)
    }

    @Test
    fun `subrange retains absolute stream offset and all url parameters`() {
        val resolved = dataSpec(position = 128)
            .buildUpon()
            .setUriPositionOffset(64)
            .build()
            .withStreamUri(Uri.parse(streamUrl), chunkLength)

        assertEquals(192L, resolved.position)
        assertEquals(256L, resolved.absoluteStreamPosition)
        assertEquals(64L, resolved.uriPositionOffset)
        assertEquals(chunkLength, resolved.length)
        assertEquals("proof", resolved.uri.getQueryParameter("pot"))
        assertEquals("nonce", resolved.uri.getQueryParameter("cpn"))
        assertEquals("decoded", resolved.uri.getQueryParameter("n"))
        assertEquals("signature", resolved.uri.getQueryParameter("sig"))
    }
}
