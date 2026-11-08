# Network and external integrations

All paths are source-derived. External service behavior, field stability, quotas, operational availability and terms are unverified; examples below are schematic model shapes, not recorded traffic. App runtime URLs are distinct from build repositories and browser links.

## YouTube Music / InnerTube (unofficial, private)

[`Innertube.kt`](../innertube/src/main/kotlin/it/vfsfitvnm/innertube/Innertube.kt) constructs Ktor 2.1.2 `HttpClient(OkHttp)`, browser User-Agent, `expectSuccess=true`, Kotlin serialization (ignore unknown keys, omit explicit nulls, encode defaults), Brotli decoding. Default host is `https://music.youtube.com`, JSON `Content-Type`, `X-Goog-Api-Key: [REDACTED]`, query `prettyPrint=false`; individual functions add `X-Goog-FieldMask`. The key is an embedded client identifier, not a protected credential; its location is documented, not its value. A static `visitorData` is embedded in `models/Context.kt` (redacted here). Search/browse/next use `WEB_REMIX` 1.20220918 desktop; player uses `ANDROID_MUSIC` 5.28.1 mobile, Android SDK 30 and client user agent in JSON. The age-restriction retry uses `TVHTML5_SIMPLY_EMBEDDED_PLAYER` 2.0 with `thirdParty.embedUrl`.

| Method and path under `/youtubei/v1/` | Request → response / usage | Implementation |
|---|---|---|
| POST `search` | `SearchBody(context,query,params)` filter or `ContinuationBody(context,continuation)` → `SearchResponse`/`ContinuationResponse`, `ItemsPage` for songs/videos/albums/artists/playlists | `requests/SearchPage.kt`; filter constants in `Innertube.kt` |
| POST `music/get_search_suggestions` | `SearchSuggestionsBody(input,context)` → `SearchSuggestionsResponse` | `requests/SearchSuggestions.kt` |
| POST `browse` | `BrowseBody(browseId,params,context)` or continuation → `BrowseResponse` / `ContinuationResponse`; album, artist, remote playlist, sections, lyrics and related page | `requests/AlbumPage.kt`, `ArtistPage.kt`, `PlaylistPage.kt`, `ItemsPage.kt`, `Lyrics.kt`, `RelatedPage.kt` |
| POST `next` | `NextBody(videoId, playlistId, params, playlistSetVideoId, index, isAudioOnly, tunerSettingValue, watchEndpointMusicSupportedConfigs, context)` or continuation → `NextResponse`; queue/radio, related and lyrics browse IDs | `requests/NextPage.kt`, `RelatedPage.kt`, `Lyrics.kt` |
| POST `music/get_queue` | `QueueBody(videoIds, playlistId, context)` → `GetQueueResponse`, including `song(videoId)` lookup | `requests/Queue.kt` |
| POST `player` | `PlayerBody(videoId,playlistId,context)` → `PlayerResponse` (playabilityStatus, audio config, adaptive formats, videoDetails) | `requests/Player.kt`; playback uses it in `PlayerService.createDataSourceFactory` |

Illustrative search (omitting default fields) and response, not a live capture:

```http
POST https://music.youtube.com/youtubei/v1/search?prettyPrint=false
Content-Type: application/json
X-Goog-Api-Key: [REDACTED]
X-Goog-FieldMask: [renderer mask]

{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20220918","platform":"DESKTOP","visitorData":"[REDACTED]"}},"query":"example song","params":"[song filter]"}
```

```json
{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{"flexColumns":[]}}],"continuations":[]}}]}}}}]}}}
```

Example player body: `{"context":{"client":{"clientName":"ANDROID_MUSIC","clientVersion":"5.28.1","platform":"MOBILE","visitorData":"[REDACTED]"}},"videoId":"[VIDEO_ID]"}`. A successful modeled response may contain `{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","url":"[REDACTED SIGNED URL]"}]},"videoDetails":{"videoId":"[VIDEO_ID]"}}`; actual serializer-required nullable fields and response shape depend on provider. Never publish signed URLs.

`runCatchingNonCancellable` returns `Result` on success/failure and null on cancellation; it does not implement a retry policy. `player()` tries a second client only if the first response has non-OK status; if the second remains non-OK it returns the original response. If the first request throws, no second attempt is made. Many screens collapse failures to empty/null or display errors. No provider quota/rate limits are defined in the repo.

**Authentication and extraction:** no account login, cookie jar, OAuth, proof-of-origin token generation, dynamic visitor acquisition, signature cipher deciphering, `n` transformation, player JavaScript download or signature timestamp implementation is found. `PlayerResponse` models only direct `url`; it selects the last format with itag 251 or 140, not a bitrate sort. The player endpoint's adaptive format URLs are passed to HTTP; no app-level SABR, DASH manifest or HLS pipeline is implemented. Missing/cipher-only URLs and changes to provider behavior can break playback. Playback uses Media3's Matroska and fragmented MP4 extractors, not NewPipe. See [playback](PLAYBACK_ENGINE.md).

## Piped-compatible third party (conditional)

`GET https://watchapi.whatever.social/streams/{videoId}` in `requests/Player.kt` is called only when the primary player status is not OK **and** the embedded-client retry returns OK. JSON `{"audioStreams":[{"url":"[SIGNED URL]","bitrate":128000}]}` is decoded into function-local `PipedResponse`/`AudioStream`, then URLs are matched by exact bitrate to embedded `adaptiveFormats`. There is no fallback if the primary request succeeds but has no usable URL, or the embedded player is non-OK. A bitrate mismatch can produce null URLs. The default Ktor client also configures API-key headers and query globally; verify whether they are carried to this absolute external URL in a request capture and avoid cross-origin credential propagation. No independent authentication or retry is implemented. Provider access policy, deployment, and availability are unknown. This service receives the video ID and network address.

## KuGou (third-party lyrics)

[`KuGou.kt`](../kugou/src/main/kotlin/it/vfsfitvnm/kugou/KuGou.kt) uses Ktor OkHttp, browser User-Agent, gzip/deflate and accepts JSON under JSON, text/html or text/plain. No account auth is configured. GET requests:

* `https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=...` → `SearchSongResponse.data.info` (hash, duration).
* `https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&hash=...` or `keyword=...` → `SearchLyricsResponse.candidates` (id, accessKey).
* `https://krcs.kugou.com/download?ver=1&man=yes&client=pc&fmt=lrc&id=...&accesskey=[REDACTED]` → `DownloadLyricsResponse.content`, base64-encoded LRC.

Duration match tolerance increases from 0 to 5 seconds, then keyword-only fallback. First candidate wins; `Result` conveys failure and cancellation becomes null. Limits, provider terms and live responses unknown. Full flow: [lyrics](LYRICS_SYSTEM.md).

## Other runtime connections and boundaries

* Audio: Media3 `DefaultHttpDataSource` GETs provider-returned signed URLs/byte ranges; hosts are dynamic, not hardcoded or guaranteed. Firefox 91 User-Agent, connect timeout 16s, read timeout 8s; no per-stream extractor headers are supplied. Debug builds log only sanitized position, length, status, and selected validated response headers; see [playback diagnostics](PLAYBACK_ENGINE.md). This is media delivery, not NewPipe.
* Artwork: Coil fetches URLs provided in metadata; code explicitly recognizes `i.ytimg.com`, `lh3.googleusercontent.com`, `yt3.ggpht.com` for thumbnail handling. Other server-provided image hosts can occur. Android/framework and Coil behavior governs redirects/cache.
* Links: sharing/opening YouTube links, GitHub project/issue links, and `ACTION_WEB_SEARCH` for lyrics hand control to external apps; these are not backend API integrations. Inbound YouTube deep links are handled by `MainActivity`.
* No source evidence of analytics/crash reporting SDK, a download manager, WebView, account sync or other runtime services. Local Media3 playback statistics are stored in Room, not sent to an analytics endpoint. This does not prove all transitive library behavior absent.
* Build/CI only: Google Maven, Maven Central, JitPack (configured, not evidence of an actual dependency), Gradle distribution service and GitHub Actions/artifacts. Root README remote badges are documentation resources, not app traffic.
