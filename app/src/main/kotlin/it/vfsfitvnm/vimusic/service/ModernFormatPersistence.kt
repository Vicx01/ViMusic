package it.vfsfitvnm.vimusic.service

import androidx.media3.common.MediaItem
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.Format
import it.vfsfitvnm.vimusic.service.innertubex.StreamSnapshot

/** Preserve legacy cache metadata: the shared Format row cannot describe both encodings. */
internal fun persistModernFormat(database: Database, mediaItem: MediaItem?, stream: StreamSnapshot, legacyBytes: Long) {
    if (mediaItem == null) return
    database.insert(mediaItem)
    if (legacyBytes > 0) return
    database.insert(Format(
        songId = mediaItem.mediaId,
        itag = stream.itag,
        mimeType = stream.mimeType,
        bitrate = stream.bitrate,
        loudnessDb = stream.loudnessDb,
        contentLength = stream.contentLength
    ))
}
