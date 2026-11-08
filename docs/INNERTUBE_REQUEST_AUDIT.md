# InnerTube player 400 investigation

## Observed incident and scope

The user reported a live device request to `music.youtube.com`: stage `InnerTube player (Android client)`, HTTP 400, `FAILED_PRECONDITION`, with the API-key header present. No server message, full response body, track, account state or device/network trace was supplied. The CI probes below reproduce the same status for two independent public fixture videos from a GitHub-hosted IP; that is corroboration, not proof that the server used the same internal reason on the user's device. We have not established a configuration that produces a playable audio URL, and there is no confirmed playback fix.

## Actual request construction

`PlayerService` supplies `PlayerBody(videoId)` to `Innertube.player`. With no playlist ID it uses a Ktor POST to `https://music.youtube.com/youtubei/v1/player?prettyPrint=false`. The request includes JSON content type, a field mask, `X-Goog-Api-Key` and Ktor 2.1.2's `BrowserUserAgent()`: a Linux/Chromium 70 desktop browser UA. It does **not** set `X-YouTube-Client-Name` or `X-YouTube-Client-Version`. These observations are covered by a MockEngine test configured with the same production client defaults. Tests do not print the API-key value.

The JSON is structurally:

```json
{
  "context": {
    "client": {
      "clientName": "ANDROID_MUSIC",
      "clientVersion": "5.28.1",
      "platform": "MOBILE",
      "hl": "en",
      "visitorData": "[REDACTED STATIC VALUE]",
      "androidSdkVersion": 30,
      "userAgent": "[ANDROID MUSIC 5.28.1 UA]"
    }
  },
  "videoId": "[VIDEO ID]"
}
```

The numeric client ID for `ANDROID_MUSIC` is **21**, as identified in [InnerTubeX's client catalog](https://github.com/MetrolistGroup/innertubex/blob/64c7df4d88d27e6964157b3b341f7ec70f88ea6e/src/commonMain/kotlin/com/metrolist/innertubex/models/YouTubeClient.kt); this app sends the string name in JSON but does not send that numeric ID as a header. `ANDROID` is a different client (yt-dlp identifies it as ID 3), not another name for ANDROID_MUSIC. The JSON's native Android UA and the HTTP Linux browser UA are inconsistent; whether this causes rejection is not established. For a non-null playlist ID, the body also serializes `playlistId`.

`Context` has no `user` or `request` property. `PlayerBody` has no `playbackContext`, `signatureTimestamp`, `serviceIntegrityDimensions`, `contentCheckOk`, `racyCheckOk`, or `params` property. The literal `visitorData` is hardcoded in `models/Context.kt`, reused across requests and contains URL-encoded padding; its validity/current association with a visitor session is unknown. These omissions and the old version are factual; they are **not** by themselves proof of which precondition the server checks. Some modern implementations use flags, dynamic visitor data, a player timestamp or proof-of-origin tokens depending on the client. Do not fabricate a timestamp, token, visitor ID or claim every omitted field is universally required.

This is not a JSON client-name mix: search/browse/metadata generally use `WEB_REMIX` 1.20220918, while the player body uses `ANDROID_MUSIC` 5.28.1. They do share a music host, desktop HTTP User-Agent and one globally configured API-key header. Whether the server supports a given client on a host cannot be inferred from the hostname alone. In this probe, the old Android Music profile returned 400 on **both** music and www, while the newer version on music reached HTTP 200; thus switching host alone did not resolve the tested failure.

## API key provenance and other implementations

The app's key is a hardcoded constant in `innertube/Innertube.kt`, attached as `X-Goog-Api-Key` to the shared client; the repository does not document its original issuance or client ownership. Do not print the key, URL query if it contains a key, request bodies, raw error bodies or stack traces. The same value appears in the maintained haturatu/ViMusic fork's older WEB profile, which is evidence of reuse, **not** proof that it is exclusive to that client or that it caused the 400. In the controlled baseline, moving the same value from the header to the `key` query parameter produced the same 400 on the tested fixture. This does not establish which form is required for every client.

Source comparison (snapshots inspected 2026-11-08; these are implementation choices, not official API guarantees):

* [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/9f0ebfec2a778049a55256eefd9115791eb33972/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/ClientsConstants.java) uses WEB_REMIX ID 67, dynamically obtains visitor data for player requests and uses `contentCheckOk`/`racyCheckOk`; its current stream path uses WEB metadata and a visionOS profile, not this legacy Android Music profile.
* [yt-dlp](https://github.com/yt-dlp/yt-dlp/blob/412e7f0a4b0588fe884a02b344ac66a06538475b/yt_dlp/extractor/youtube/_base.py) selects `www.youtube.com` for its ordinary ANDROID client (ID 3) and `music.youtube.com` for WEB_REMIX (ID 67), sends matching client headers, and allows an optional API key in the query. It supports conditional player timestamps and proof tokens; token requirements depend on client and media protocol.
* [InnerTubeX](https://github.com/MetrolistGroup/innertubex/blob/64c7df4d88d27e6964157b3b341f7ec70f88ea6e/src/commonMain/kotlin/com/metrolist/innertubex/extraction/strategy/PlaybackClientCatalog.kt) marks its own ANDROID_MUSIC 5.34.51 profile probe-only/broken after 400 `FAILED_PRECONDITION`; it uses per-client hosts/headers, client contexts, and conditional timestamps/tokens. This independently resembles the user's failure but does not establish its cause here.
* [haturatu/ViMusic](https://github.com/haturatu/ViMusic/blob/8d6eb48ed5770ca6d2b004d5a96947d1c457a205/providers/youtube-music/src/main/kotlin/app/vimusic/providers/youtubemusic/innertube/requests/Player.kt) is an active ViMusic-named project with a substantially different provider implementation, iOS-first/other fallbacks, timestamps and client-specific configuration. Its behavior is not proof that this checkout can safely adopt any one of its profiles.
* The newer `8.39.42` test value appears in [Echo Music's media-client catalog](https://github.com/EchoMusicApp/Echo-Music/blob/ee10c5c8c924c676429b9e04d282c5adef7db410/app/src/main/kotlin/com/music/echo/utils/PlayerClient.kt); that source describes media-request identity, not a guarantee of a supported unauthenticated player profile. The source is a candidate, not a claim that version 8.39.42 plays successfully.

## Controlled provider tests

The opt-in `LivePlayerProbeTest` uses the app's real Ktor/OkHttp client, JSON serializer, static key/header and field mask with public fixture IDs, never user history. It prints only status, a restricted structured error summary, coarse message category, playability status and counts; no tokens, URL, ID or raw response text. Relative and absolute cases include the same `prettyPrint` parameter. Each Android comparison changes just one element relative to its stated predecessor; the WEB_REMIX control is a separate coherent profile and changes several identity fields together. The version-only test deliberately retains the old Android UA in the body to isolate the version. A field-mask-only variant requests the reason and reports only a coarse category.

CI test runs are from a datacenter IP, not the user's device. HTTP 200 is not playback success. See [the initial four-profile run](https://github.com/Vicx01/ViMusic/actions/runs/42479413971) and the expanded run recorded below; source code is in `innertube/src/test/.../LivePlayerProbeTest.kt`. The diagnostic job runs only when an explicit `[player-probe]` commit message triggers it; regular pushes skip it. The default JVM suite skips the external probe.

### Results (GitHub Actions [run 42480471115](https://github.com/Vicx01/ViMusic/actions/runs/42480471115), 2026-11-08)

The original field mask omits playability reason. `reason absent` below means not requested, **not** proof that the server supplied none. Error bodies are restricted to allowlisted fields. The legacy 400 response's message exactly matched the safe generic text `Precondition check failed.`; no more specific server cause was provided by that text. No raw bodies were retained or published.

| Comparison | One change from predecessor, except separate control | HTTP and sanitized response |
|---|---|---|
| Legacy baseline | ANDROID_MUSIC 5.28.1, music host, original key header | 400; `code=400, status=FAILED_PRECONDITION`; generic precondition message |
| Host only | Baseline on www instead of music | 400; same sanitized error |
| Key location only | Baseline key moved from header to query | 400; same sanitized error |
| Version only | Baseline `context.client.clientVersion` → 8.39.42; body UA deliberately unchanged | 200; `playability=LOGIN_REQUIRED`; 0 adaptive formats / 0 direct URLs |
| Body UA only | Previous row with matching Android Music 8.39.42 body UA | 200; `LOGIN_REQUIRED`; 0 formats / 0 direct URLs |
| HTTP UA only | Previous row with matching Android Music HTTP UA | 200; `LOGIN_REQUIRED`; 0 formats / 0 direct URLs |
| Client-name header only | Previous row plus numeric header 21 | 200; `LOGIN_REQUIRED`; 0 formats / 0 direct URLs |
| Client-version header only | Previous row plus version header 8.39.42 | 200; `LOGIN_REQUIRED`; 0 formats / 0 direct URLs |
| Reason field mask only | Previous row with playability reason requested | 200; `LOGIN_REQUIRED`; reason category `sign_in`; 0 formats / 0 direct URLs |
| Second video, baseline | Legacy baseline with a different public music-video ID | 400; same sanitized error |
| Second video, version only | Previous row with version 8.39.42 only | 200; `LOGIN_REQUIRED`; 0 formats / 0 direct URLs |
| WEB_REMIX control | Separate WEB_REMIX profile, music host, version 1.20260707.12.00, matching numeric/version headers and browser UA; no timestamp/token | 200; `LOGIN_REQUIRED`; 0 formats / 0 direct URLs |
| WEB reason field mask only | Previous row with playability reason requested | 200; `LOGIN_REQUIRED`; reason category `bot_check`; 0 formats / 0 direct URLs |

Version is therefore an experimentally significant factor in these controlled requests, whereas host-only and header-versus-query-only changes did not alter the observed 400. The legacy version is a strong compatibility suspect, but the generic 400 message does not prove the server's exact internal precondition or the user's device-specific cause. The newer client and WEB_REMIX did not yield playable streams in this environment, and the WEB control encountered a bot check; neither establishes what would happen on the user's network/device. No candidate is confirmed as a working, supported end-to-end replacement. The app's default player configuration, search/metadata, Media3, cache, queue and lyrics behavior have therefore **not** been changed.

Before adopting a player-profile change, run the same isolated comparisons for the affected video on the user's physical device/network, obtain a response with playable audio formats, and verify audio playback beyond 30 seconds including subsequent byte ranges. Capture only approved status/reason categories and counts; do not collect video IDs, visitor data, API keys, cookies, signed URLs, proof tokens or raw response text. An authenticated or tokenized solution should not be assumed or added without separate evidence and design.
