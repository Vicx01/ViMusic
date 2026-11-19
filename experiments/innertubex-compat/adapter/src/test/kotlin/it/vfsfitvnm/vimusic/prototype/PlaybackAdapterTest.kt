package it.vfsfitvnm.vimusic.prototype

import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PlaybackAdapterTest {
    @Test fun constructionAndCleanupRequireNoNetwork() = runBlocking {
        val client = HttpClient(MockEngine { error("Unexpected network request") })
        val tokens = object : TokenProvider {
            override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? = null
        }
        try {
            val adapter = PlaybackAdapter(client, tokens)
            adapter.close()
        } finally {
            client.close()
        }
    }
}
