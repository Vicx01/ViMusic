# Development guide

Read [architecture](ARCHITECTURE.md), [API contracts](API_INTEGRATIONS.md) and [build/testing](BUILD_AND_TESTING.md) first. Follow existing Kotlin official style (`gradle.properties`), Compose composables, nullable `Result` conventions and coroutine cancellation behavior. Avoid putting credentials or signed URLs in tests, logs or docs. No CI coverage threshold exists; the tests below are recommendations, not an existing enforced policy.

## Add a feature or screen

1. Trace the existing screen under `app/.../ui/screens/`, route in `Routes.kt`/`HomeScreen`, and how binder/DAO/client are acquired. Add a route to the custom `compose-routing` system and connect its handler; do not assume Jetpack Navigation.
2. Model transient UI state using the existing `remember`, `rememberSaveable`, `persist` and/or Flow patterns; use `rememberPreference` only for durable settings. Respect lifecycle, coroutine cancellation and null binder states.
3. For local persistent changes add/update Room entities and DAO operations in `models/` and `Database.kt`. Bump the schema, check in the export, write appropriate migration(s) and test upgrades from all supported versions. Test UI states and failure cases.

## Change an API integration

Inspect `innertube/requests`, `models` and serialized `bodies`, or `kugou/KuGou.kt`; update client/model/converter together. Respect provider terms and avoid assumptions about private response stability. Add redacted fixtures and fake transport tests for success, missing fields, cancellation, HTTP errors, pagination and origin-specific headers. Update [API documentation](API_INTEGRATIONS.md). Do not record real keys, access keys, visitor IDs or stream URLs.

## Change playback

Trace `utils/Utils.kt` MediaItem/cache key, `utils/Player.kt`, `PlayerService.createDataSourceFactory`, `requests/Player.kt` and `Thumbnail.kt` error display. Before changing resolver/range/cache logic, reproduce with deterministic HTTP fixtures and verify seeks, first and subsequent opens, cache hits/eviction, expired links, status mapping, long playback, queue transitions and background sessions. Test foreground lifecycle, audio focus and process death on devices. Document changes in [playback](PLAYBACK_ENGINE.md), especially what is measured versus hypothetical.

## Change lyrics

Follow `ui/screens/player/Lyrics.kt`, `requests/Lyrics.kt`, `kugou/KuGou.kt` and `utils/SynchronizedLyrics.kt`; preserve the null/empty meaning and the song foreign key. Add parsing, matching, sync/seek and provider-failure tests before changing format behavior. Keep [lyrics](LYRICS_SYSTEM.md) accurate. This audit does not implement a karaoke redesign.

For every change, build with the supported environment, execute relevant tests and explicitly report unavailable tests. Consider accessibility, local data migration, privacy/permissions, licensing and unofficial provider availability. Small changes should not silently alter other service behavior.
