package it.vfsfitvnm.vimusic.service.innertubex

import android.content.Context
import android.util.Log
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import it.vfsfitvnm.vimusic.service.innertubex.potoken.PoTokenGenerator
import kotlinx.coroutines.CancellationException

/** Anonymous visitor tokens only; ViMusic does not forward account cookies to WebView. */
internal class WebBotGuardTokenProvider(context: Context) : TokenProvider {
    private val generator = PoTokenGenerator(context.applicationContext)
    override val capabilities = TokenProviderCapabilities(
        providers = setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true
    )

    override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? {
        if (visitorData.isBlank()) return null
        return try {
            generator.getWebClientPoToken(videoId, visitorData)?.let {
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
