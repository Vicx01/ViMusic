package it.vfsfitvnm.kugou

import it.vfsfitvnm.kugou.models.DownloadLyricsResponse
import it.vfsfitvnm.kugou.models.SearchLyricsResponse
import it.vfsfitvnm.kugou.models.SearchSongResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Offline fixture coverage for the models used by the existing lyrics provider. */
@OptIn(ExperimentalSerializationApi::class)
class LyricsSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun lyricsProviderModelsDeserializeWithUnknownFields() {
        val songs = json.decodeFromString<SearchSongResponse>(
            """{"data":{"info":[{"duration":123,"hash":"fixture-hash","extra":true}]},"extra":1}"""
        )
        assertEquals(123L, songs.data.info.single().duration)
        assertEquals("fixture-hash", songs.data.info.single().hash)

        val candidates = json.decodeFromString<SearchLyricsResponse>(
            """{"candidates":[{"id":42,"accesskey":"fixture","duration":123,"extra":true}]}"""
        )
        assertEquals(42L, candidates.candidates.single().id)
        assertEquals("fixture", candidates.candidates.single().accessKey)
        assertEquals(123L, candidates.candidates.single().duration)

        val download = json.decodeFromString<DownloadLyricsResponse>(
            """{"content":"bHlyaWNz","extra":true}"""
        )
        assertEquals("bHlyaWNz", download.content)
    }
}
