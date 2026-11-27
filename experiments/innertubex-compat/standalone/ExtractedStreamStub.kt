// Test-only stand-in, based on the fields used from InnerTubeX v0.11.6.
// It does not establish binary or Gradle compatibility with the real artifact.
package com.metrolist.innertubex.extraction

import kotlin.time.Instant

class ExtractedStream(
    val videoId: String,
    val audioUrl: String,
    val headers: Map<String, String>,
    val loudnessDb: Double?,
    val expiresAt: Instant?,
    val contentLengthBytes: Long?,
    val itag: Int,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val clientName: String,
    val profileId: String,
    val requireBoundedRange: Boolean,
    val rangeChunkSizeBytes: Long,
    val useRangeChunks: Boolean,
    val sabrBootstrap: Any? = null,
)
