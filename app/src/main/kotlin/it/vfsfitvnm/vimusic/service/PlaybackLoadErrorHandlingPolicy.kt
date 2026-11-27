package it.vfsfitvnm.vimusic.service

import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import it.vfsfitvnm.innertube.requests.PlayerRequestException

/** Preserve the previous non-retrying behavior for Ktor HTTP response errors. */
internal class PlaybackLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long =
        if (loadErrorInfo.exception is PlayerRequestException) C.TIME_UNSET
        else super.getRetryDelayMsFor(loadErrorInfo)
}
