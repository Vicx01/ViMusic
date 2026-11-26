package it.vfsfitvnm.vimusic.service.innertubex

import android.content.Context
import android.util.Log
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import it.vfsfitvnm.vimusic.service.innertubex.potoken.PoTokenGenerator
import kotlinx.coroutines.CancellationException

internal interface BotGuardMinter {
    suspend fun mint(videoId: String, visitorData: String): it.vfsfitvnm.vimusic.service.innertubex.potoken.PoTokenResult?
    suspend fun close()
}

/** Anonymous visitor tokens only; ViMusic does not forward account cookies to WebView. */
internal class WebBotGuardTokenProvider(private val generator: BotGuardMinter) : TokenProvider {
    constructor(context: Context) : this(object : BotGuardMinter {
        private val impl = PoTokenGenerator(context.applicationContext)
        override suspend fun mint(videoId: String, visitorData: String) = impl.getWebClientPoToken(videoId, visitorData)
        override suspend fun close() = impl.close()
    })

    override val capabilities = TokenProviderCapabilities(
        providers = setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true
    )

    override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? {
        if (visitorData.isBlank()) return null
        return try {
            generator.mint(videoId, visitorData)?.let {
                PoTokenResult(it.playerRequestPoToken, it.streamingDataPoToken, visitorData)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // InnerTubeX may try another client; no raw exception or response is logged.
            Log.w("ViMusicPlayback", "BotGuard token generation failed; trying another client")
            throw error
        }
    }

    override suspend fun invalidateAttestation() = generator.close()
    override suspend fun close() = generator.close()
}
