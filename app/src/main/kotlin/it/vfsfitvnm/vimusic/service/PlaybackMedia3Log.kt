package it.vfsfitvnm.vimusic.service

import android.util.Log as AndroidLog
import androidx.media3.common.util.Log as Media3Log

/** Media3 logs full source-error stack traces, including Ktor messages containing URLs/bodies. */
internal fun safeMedia3LogMessage(tag: String, message: String): String = when {
    tag == "ExoPlayerImplInternal" && message.startsWith("Playback error\n") ->
        "Playback error (see PlaybackError for sanitized details)"
    tag == "LoadTask" && message.startsWith("Unexpected exception loading stream\n") ->
        "Unexpected exception loading stream (see PlaybackError for sanitized details)"
    else -> message
}

/** Only redact Media3's two known full playback stack traces; all other logs pass through. */
internal fun installPlaybackMedia3Log() {
    Media3Log.setLogger(object : Media3Log.Logger {
        override fun d(tag: String, message: String) { AndroidLog.d(tag, safeMedia3LogMessage(tag, message)) }
        override fun i(tag: String, message: String) { AndroidLog.i(tag, safeMedia3LogMessage(tag, message)) }
        override fun w(tag: String, message: String) { AndroidLog.w(tag, safeMedia3LogMessage(tag, message)) }
        override fun e(tag: String, message: String) { AndroidLog.e(tag, safeMedia3LogMessage(tag, message)) }
    })
}
