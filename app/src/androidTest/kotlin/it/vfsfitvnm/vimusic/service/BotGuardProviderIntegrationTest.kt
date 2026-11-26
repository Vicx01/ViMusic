package it.vfsfitvnm.vimusic.service

import androidx.media3.datasource.ResolvingDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import it.vfsfitvnm.vimusic.service.innertubex.BotGuardMinter
import it.vfsfitvnm.vimusic.service.innertubex.ModernDataSpecResolver
import it.vfsfitvnm.vimusic.service.innertubex.WebBotGuardTokenProvider
import it.vfsfitvnm.vimusic.service.innertubex.potoken.PoTokenResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BotGuardProviderIntegrationTest {
    @Test fun botGuardProviderMapsTokensAndReleasesMinter() = runBlocking {
        var calls = 0
        var closes = 0
        val provider = WebBotGuardTokenProvider(object : BotGuardMinter {
            override suspend fun mint(videoId: String, visitorData: String): PoTokenResult {
                calls++
                assertEquals("video", videoId)
                assertEquals("visitor", visitorData)
                return PoTokenResult("player-fixture", "stream-fixture")
            }
            override suspend fun close() { closes++ }
        })
        assertTrue(provider.capabilities.usesWebView)
        assertTrue(provider.capabilities.providers.contains(PoTokenProviderKind.WEB_BOTGUARD))
        assertNull(provider.getPoToken("video", "", null))
        assertEquals(0, calls)
        val token = checkNotNull(provider.getPoToken("video", "visitor", null))
        assertEquals("player-fixture", token.playerRequestToken)
        assertEquals("stream-fixture", token.streamingDataToken)
        assertEquals("visitor", token.visitorData)
        assertFalse(token.toString().contains("fixture"))
        provider.invalidateAttestation()
        provider.close()
        assertEquals(2, closes)
    }

    @Test fun botGuardFailureReachesConnectedLegacyFallbackAndCancellationEscapes() {
        val provider = WebBotGuardTokenProvider(object : BotGuardMinter {
            override suspend fun mint(videoId: String, visitorData: String): PoTokenResult = throw IOException("untrusted fixture")
            override suspend fun close() = Unit
        })
        var failures = 0
        var fallbacks = 0
        val legacy = ResolvingDataSource.Resolver { fallbacks++; it }
        val resolver = ModernDataSpecResolver({ _, _ -> false }, {
            runBlocking { provider.getPoToken("video", "visitor", null) }
            error("token failure must propagate")
        }, legacy, { failures++ })
        val spec = androidx.media3.datasource.DataSpec.Builder().setUri("song").setKey("song").build()
        assertEquals(spec, resolver.resolveDataSpec(spec))
        assertEquals(1, failures)
        assertEquals(1, fallbacks)

        val cancelled = WebBotGuardTokenProvider(object : BotGuardMinter {
            override suspend fun mint(videoId: String, visitorData: String): PoTokenResult = throw CancellationException("cancel")
            override suspend fun close() = Unit
        })
        try { runBlocking { cancelled.getPoToken("video", "visitor", null) }; fail("expected cancellation") }
        catch (_: CancellationException) { }
    }
}
