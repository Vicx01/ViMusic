package it.vfsfitvnm.vimusic.service

import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/** Debug-only HTTP diagnostics; deliberately excludes URI, key, body and arbitrary headers. */
internal object PlaybackNetworkDiagnostics : TransferListener {
    private val contentLengthPattern = Regex("[0-9]{1,20}")
    private val contentRangePattern = Regex("bytes (?:[0-9]{1,20}-[0-9]{1,20}/(?:[0-9]{1,20}|\\*)|\\*/[0-9]{1,20})", RegexOption.IGNORE_CASE)
    private val acceptRangesPattern = Regex("bytes|none", RegexOption.IGNORE_CASE)

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (!isNetwork) return
        Log.d(
            "PlaybackHttp",
            "position=${dataSpec.position} length=${dataSpec.length} " +
                "status=${(source as? HttpDataSource)?.responseCode ?: -1} " +
                safeAudioResponseHeaders(source.responseHeaders)
        )
    }

    internal fun safeAudioResponseHeaders(headers: Map<String, List<String>>): String {
        fun header(name: String, pattern: Regex): String = headers.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value?.firstOrNull()?.takeIf { pattern.matches(it) } ?: "absent or invalid"
        return "Content-Length=${header("Content-Length", contentLengthPattern)} " +
            "Content-Range=${header("Content-Range", contentRangePattern)} " +
            "Accept-Ranges=${header("Accept-Ranges", acceptRangesPattern)}"
    }

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) = Unit
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
}
