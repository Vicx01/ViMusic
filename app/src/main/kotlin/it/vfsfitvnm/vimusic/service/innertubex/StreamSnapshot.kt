package it.vfsfitvnm.vimusic.service.innertubex

import com.metrolist.innertubex.extraction.ExtractedStream

/** Stream credentials are never included in diagnostic string representations. */
internal class StreamSnapshot private constructor(
    val url: String,
    val headers: Map<String, String>,
    val expiresAtMs: Long?,
    val clientName: String,
    val itag: Int,
    val mimeType: String?,
    val bitrate: Long?,
    val contentLength: Long?,
    val loudnessDb: Float?,
    val requireBoundedRange: Boolean,
    val useRangeChunks: Boolean,
    val rangeChunkSizeBytes: Long,
) {
    // The real Media3 bridge must implement a multi-request transport before
    // accepting bounded streams; merely truncating DataSpec signals end-of-input.
    val requiresChunkTransport: Boolean get() = requireBoundedRange || useRangeChunks

    override fun toString(): String = "StreamSnapshot(redacted)"

    companion object {
        fun from(stream: ExtractedStream): StreamSnapshot {
            require(stream.sabrBootstrap == null) { "SABR transport is not implemented" }
            return StreamSnapshot(
                url = stream.audioUrl,
                headers = stream.headers.toMap(),
                expiresAtMs = stream.expiresAt?.toEpochMilliseconds(),
                clientName = stream.clientName,
                itag = stream.itag,
                mimeType = stream.mimeType,
                bitrate = stream.bitrate?.toLong(),
                contentLength = stream.contentLengthBytes,
                loudnessDb = stream.loudnessDb?.toFloat(),
                requireBoundedRange = stream.requireBoundedRange,
                useRangeChunks = stream.useRangeChunks,
                rangeChunkSizeBytes = stream.rangeChunkSizeBytes,
            )
        }
    }
}

/** In-memory URL metadata only; never touches Media3's on-disk audio cache. */
internal class StreamCache(initialGeneration: Long) {
    // Signed URLs are short-lived; do not retain an unbounded history in memory.
    private val entries = object : LinkedHashMap<String, StreamSnapshot>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamSnapshot>?): Boolean = size > 20
    }
    private var activeGeneration = initialGeneration

    /** Must be called by the host for logout, account or visitor-session changes. */
    @Synchronized
    fun changeSession(generation: Long) {
        require(generation > activeGeneration) { "Session generation must increase" }
        activeGeneration = generation
        entries.clear()
    }

    @Synchronized
    fun put(videoId: String, generation: Long, stream: StreamSnapshot, nowMs: Long) {
        // An older in-flight resolution must never repopulate a newer session.
        if (generation != activeGeneration) return
        // Unknown expiry is not guessed: require fresh resolution instead.
        if (stream.expiresAtMs == null || stream.expiresAtMs <= nowMs) {
            entries.remove(videoId)
        } else {
            entries[videoId] = stream
        }
    }

    @Synchronized
    fun get(videoId: String, generation: Long, nowMs: Long): StreamSnapshot? {
        if (generation != activeGeneration) return null
        val stream = entries[videoId] ?: return null
        if (stream.expiresAtMs == null || stream.expiresAtMs <= nowMs) {
            entries.remove(videoId)
            return null
        }
        return stream
    }

    @Synchronized
    fun invalidate(videoId: String, generation: Long) {
        if (generation == activeGeneration) entries.remove(videoId)
    }
}
