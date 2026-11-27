# Lyrics system

The player overlay [`ui/screens/player/Lyrics.kt`](../app/src/main/kotlin/it/vfsfitvnm/vimusic/ui/screens/player/Lyrics.kt) receives `mediaId`, metadata and duration providers and an insert callback. It observes `Database.lyrics(mediaId)` and stores one Room row per song with `fixed` and `synced` fields. `null` means not fetched; an empty string records no lyrics found. Switching the SharedPreferences flag chooses which field to show; users can edit, clear/refetch, or send a web-search intent. In normal fetch paths the song must exist for the lyrics FK; the edit path explicitly calls `ensureSongInserted`.

## Plain lyrics

`Innertube.lyrics(NextBody(videoId))` in [`requests/Lyrics.kt`](../innertube/src/main/kotlin/it/vfsfitvnm/innertube/requests/Lyrics.kt) POSTs `next`, reads the browse endpoint at tab index 1 (assuming it is the lyrics tab), POSTs `browse`, and reads the description text. It is plain unsynchronized text, persisted in `fixed`. Missing content becomes an empty string; network errors show the fetch-error overlay. See [API contracts](API_INTEGRATIONS.md).

## Synchronized lyrics

When `synced` is null, the overlay waits in 100 ms intervals until ExoPlayer duration is known, then calls `KuGou.lyrics(artist,title,durationSeconds)`. The client normalizes artist/title (including feat. handling), searches song metadata, selects a duration match within increasing 0–5 second tolerance, searches by hash, then falls back to keyword. It downloads the first candidate's base64 `fmt=lrc` content and normalizes it. A mismatch can associate lyrics with the wrong recording; no authenticated canonical ID lookup is done.

`KuGou.Lyrics.normalize` removes selected LRC metadata/credits, normalizes line endings and replaces `&apos;`. `sentences` prepends `(0, "")`, then reads digits from fixed positions in the first 10 characters (expected `[mm:ss.xx]`) into milliseconds and takes text from index 10. Malformed lines are silently skipped; arbitrary timestamp precision, multiple timestamps per line, metadata offsets, TTML and word-level timing are not implemented. Do not infer support for every LRC variant.

`utils/SynchronizedLyrics.kt` compares current player position to timestamps and selects the latest strictly earlier line; `Lyrics.kt` polls every 50 ms using player position plus 50 ms and scrolls a LazyColumn, highlighting the current line. This is line synchronization, not karaoke word highlighting. When visible in synchronized mode it keeps the screen on. Seek behavior is driven by the current position, but no automated accuracy tests exist. Lyrics are local Room data and can be edited independently of providers.
