import it.vfsfitvnm.innertube.models.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerResponseTest {
    private fun format(
        itag: Int,
        bitrate: Long,
        url: String? = "https://example.com/$itag",
        mimeType: String = "audio/webm"
    ) = PlayerResponse.StreamingData.AdaptiveFormat(
        itag = itag,
        mimeType = mimeType,
        bitrate = bitrate,
        averageBitrate = bitrate,
        url = url
    )

    @Test
    fun `skips formats without a playable audio url`() {
        val streamingData = PlayerResponse.StreamingData(
            adaptiveFormats = listOf(
                format(itag = 251, bitrate = 130_000, url = null),
                format(itag = 140, bitrate = 128_000, mimeType = "audio/mp4")
            )
        )

        assertEquals(140, streamingData.highestQualityFormat?.itag)
    }

    @Test
    fun `uses highest bitrate when preferred itags are unavailable`() {
        val streamingData = PlayerResponse.StreamingData(
            adaptiveFormats = listOf(
                format(itag = 249, bitrate = 50_000),
                format(itag = 250, bitrate = 70_000)
            )
        )

        assertEquals(250, streamingData.highestQualityFormat?.itag)
    }

    @Test
    fun `returns null when no direct audio stream is available`() {
        val streamingData = PlayerResponse.StreamingData(
            adaptiveFormats = listOf(
                format(itag = 251, bitrate = 130_000, url = null),
                format(itag = 137, bitrate = 4_000_000, mimeType = "video/mp4")
            )
        )

        assertNull(streamingData.highestQualityFormat)
    }
}
