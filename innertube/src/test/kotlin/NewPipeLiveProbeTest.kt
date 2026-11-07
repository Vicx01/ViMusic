import it.vfsfitvnm.innertube.requests.NewPipePlayer
import java.net.HttpURLConnection
import java.net.URI
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in live diagnostic, not a deterministic unit test. */
class NewPipeLiveProbeTest {
    @Test
    fun `subsequent googlevideo ranges remain authorized`() {
        assumeTrue(System.getProperty("liveNewPipeProbe") == "true")

        val response = NewPipePlayer.player("aqz-KE-bpKQ")
        val url = response.streamingData?.highestQualityFormat?.url
        requireNotNull(url) { "No playable audio URL returned" }

        val parameters = URI(url).query.orEmpty().split('&').map { it.substringBefore('=') }
        println("Live stream parameters: ${parameters.sorted()}")

        listOf(0L, 512L * 1024, 1024L * 1024, 2048L * 1024, 4096L * 1024).forEach { position ->
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 16_000
                connection.readTimeout = 16_000
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
                )
                connection.setRequestProperty("Range", "bytes=$position-${position + 1023}")

                val status = connection.responseCode
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                println(
                    "Range $position: HTTP $status ${connection.responseMessage}; " +
                        "Content-Range=${connection.getHeaderField("Content-Range")}; $error"
                )
                assertTrue("Range $position failed with HTTP $status: $error", status in 200..299)
            } finally {
                connection.disconnect()
            }
        }
    }
}
