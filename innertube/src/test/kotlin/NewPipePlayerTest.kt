import it.vfsfitvnm.innertube.requests.toAdaptiveFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream

class NewPipePlayerTest {
    @Test
    fun `preserves every stream url parameter for ExoPlayer`() {
        val url = "https://rr1---sn-example.googlevideo.com/videoplayback" +
            "?expire=123&ei=abc&ip=1.2.3.4&id=video-id&itag=140&source=youtube" +
            "&requiressl=yes&spc=token&sig=signature&n=decoded&cpn=nonce&pot=proof" +
            "&range=0-524287&rm=abc&rr=def"

        val stream = AudioStream.Builder()
            .setId("140")
            .setContent(url, true)
            .setMediaFormat(MediaFormat.M4A)
            .setAverageBitrate(128_000)
            .build()

        val format = stream.toAdaptiveFormat(durationMs = 180_000)

        assertEquals(url, format.url)
        assertEquals("audio/mp4", format.mimeType)
        assertEquals(128_000L, format.averageBitrate)
        assertEquals(180_000L, format.approxDurationMs)
    }
}
