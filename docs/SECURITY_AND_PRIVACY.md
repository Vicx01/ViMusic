# Security, privacy and licensing review

This is a static review, not a penetration test or legal opinion. No live traffic, CVE database, Android backup behavior, merged manifest or provider policy was verified.

## Permissions and exposed components

[`app/src/main/AndroidManifest.xml`](../app/src/main/AndroidManifest.xml) declares `INTERNET` (network), `POST_NOTIFICATIONS` (Android 13 notifications), `WAKE_LOCK` (background playback), `FOREGROUND_SERVICE` (media playback), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (user-facing exemption request). It declares package-visibility queries for the audio effects panel and web search. No microphone, location, contacts or storage permission is explicitly declared; the document picker handles import/export.

`MainActivity` is exported for launcher/leanback and browsable YouTube URL patterns; validate untrusted deep-link inputs. `PlayerService` and notification dismissal receiver are non-exported. `PlayerMediaBrowserService` is exported but disabled in the manifest until enabled in settings; its `onGetRoot` allows same UID, system UID or the Android Auto package name. Review authorization and callback behavior when enabling it, including Android's package/UID validation; no exploit is established. Android Auto support should not be interpreted as enabled by default.

## Secrets and transport

`innertube/.../Innertube.kt` embeds an API key and `innertube/.../models/Context.kt` embeds static visitor data; both are extractable from source/APK and must not be treated as confidential or as user credentials. They are redacted throughout these docs. The shared InnerTube Ktor `defaultRequest` adds the API-key header even when `Player.kt` requests the absolute third-party Piped URL; verify actual Ktor header behavior on the wire and scope headers to their intended origin in a future change. This audit makes no behavior change. KuGou lyric access keys and signed streaming URLs must also be treated as sensitive and excluded from logs/reports.

Source-defined API requests are HTTPS; dynamic media/artwork URLs come from responses. The debug manifest now supplies a network security config explicitly allowing cleartext to `127.0.0.1` for the local instrumentation server (other hosts use platform defaults); the main/release manifest has no custom network security config, certificate pinning or explicit `usesCleartextTraffic` override. Platform defaults depend on Android version and target SDK; this does not establish that all transitive or dynamic traffic is HTTPS. Review redirects, hostname trust and final URLs. There is no account login, cookies, token storage, analytics SDK or crash-reporting integration found in app code; this is a source finding, not a runtime guarantee. Debug playback diagnostics log only validated range/length headers, byte position, HTTP status, and exception class names/codes; they deliberately omit URLs, arbitrary headers, response bodies and exception messages. Transitive logging and runtime OS logs were not audited.

## Data disclosure and retention

Search queries and track IDs go to YouTube Music; selected conditional video IDs go to the Piped-compatible operator; artist/title/duration go to KuGou when synchronized lyrics are requested; artwork and audio hosts receive requests and IP addresses. Users' local database includes listening events, favorites, search history, playlists and lyrics; private SharedPreferences store settings. Audio/artwork caches are in app cache storage. `android:allowBackup="true"` allows Android backup subject to platform rules; exported SQLite backups are unencrypted user-selected documents and contain personal history. Imported databases are trusted user-selected files: review validation, integrity, failed-copy rollback and process restart. Document and test retention/deletion and privacy disclosure.

## Supply chain

Root GPLv3 text and dependency licensing caveats are in [dependencies](DEPENDENCIES.md). Release is signed with the debug signing config in `app/build.gradle.kts`, unsuitable as a production signing policy. The wrapper downloads Gradle without a configured SHA-256; dependencies lack locking/verification metadata and CI uses old action versions. Update in a separate verified maintenance change. No vulnerability scanner or complete SBOM was run, so no specific CVE or lack of vulnerabilities is asserted.
