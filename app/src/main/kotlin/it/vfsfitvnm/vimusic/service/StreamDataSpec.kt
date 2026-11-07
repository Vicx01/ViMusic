package it.vfsfitvnm.vimusic.service

import android.net.Uri
import androidx.media3.datasource.DataSpec

/** Applies the same bounded range to first and subsequent requests for a progressive audio URL. */
internal fun DataSpec.withStreamUri(uri: Uri, chunkLength: Long): DataSpec =
    withUri(uri).subrange(uriPositionOffset, chunkLength)
