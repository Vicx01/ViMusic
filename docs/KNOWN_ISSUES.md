# Findings and priorities

Severity expresses impact and review priority, not a claim of exploitability or measured frequency. **Confirmed** means confirmed by static source inspection only unless otherwise stated. **Suspected** means plausible, not reproduced. API 29 emulator tests passed using a local synthetic stream; live provider and physical-device behavior remain unverified.

## Critical

No confirmed critical security compromise or critical bug was established. The reported 30-second stop could be release-blocking if reproduced broadly, but impact/frequency/root cause are unknown.

## High

* **Reported ~30-second playback stop — suspected, not reproduced.** The previous resolver imposed a 512 KiB DataSpec, causing a source-confirmed premature end-of-input on that open; it has been changed to preserve the requested range. Whether this caused the reported error remains unverified. The instrumentation task passed on an API 29 emulator; gather sanitized provider/device traces; URL expiry and provider restrictions remain alternatives. [Investigation](PLAYBACK_ENGINE.md).
* **Unreliable player integration — confirmed design risk.** Private hardcoded old client profiles, static visitor data, direct URL-only parsing and no cipher/`n`/proof-token processing; conditional Piped fallback only after two specific status results and exact bitrate matching. No evidence this is the observed stop's cause. See [API integrations](API_INTEGRATIONS.md).
* **Playlist synchronization can replace a local list with a partial remote result — confirmed code path, impact untested.** `utils/Utils.kt` `completed()` breaks on a continuation failure and still returns success; `LocalPlaylistSongs.kt` then clears the local playlist and inserts the returned subset. Preserve the original unless all pages are known complete and add failure-injection tests.
* **Release signing — confirmed configuration.** `app/build.gradle.kts` release uses debug signing; replace via a proper secure release process before distributing an update. Do not document secrets.
* **Limited automated coverage.** Playback resolver mock tests supplement the previously empty client JVM tests; the player/range instrumentation task passed on an API 29 emulator with synthetic media. Migration, broad UI and live-provider coverage remain absent.

## Medium

* **Cross-origin key header risk — source-confirmed Ktor header merge, device traffic unverified.** `Innertube` default request config adds `X-Goog-Api-Key` while an absolute third-party Piped URL uses the same client. Ktor 2.1.2 merges this default header into the absolute Piped request; redirects can also carry it across authorities. Scope defaults per origin in a separately tested change. Static key is not a secret but should not be unnecessarily disclosed.
* **Error diagnosis — improved, not device-verified.** The overlay displays the sanitized cause chain, Media3 code, Ktor request stage and HTTP status; debug builds log safe HTTP headers. The synthetic emulator suite passed; actual provider and physical-device traces are still needed.
* **Restoration — confirmed limitation.** Queue saving is scheduled in `onDestroy`, which is not guaranteed after process kill; restoring clears saved rows. No proof of robust process-death restoration.
* **Database backup/import — risk.** Unencrypted exports include personal history; imported files are copied to database location. Failure/invalid-file handling and restore atomicity need dedicated tests; no data loss has been reproduced.
* **Privacy/supply chain — confirmed exposure and incomplete assurance.** `allowBackup=true`, multiple remote providers, outdated alpha/beta dependencies, no locks/checksums or vulnerability scan. See [security](SECURITY_AND_PRIVACY.md).
* **Car browser — review needed.** Exported but off by default, enabled through settings. `onGetRoot` checks UID/system/package name; authorization and callback effects need device tests. No exploit established.

## Low

* README's feature wording can imply guaranteed offline downloads/Android Auto readiness: actual offline is evictable cache and Auto is opt-in. Historical fastlane notes and root README may describe an older release rather than current provider support; docs here describe this checkout.
* `PlayerService` retains legacy cache-directory migration marked TODO. Old client versions and the `ktor-client-cio` alias pointing to OkHttp are maintenance hazards, not proof of unused dependencies.
* Custom routing, persistence, service-locator DAO and direct UI networking make ownership and testing harder. There are no ViewModels, repositories, DI framework or WorkManager in source; introducing them is not part of this audit.
* Fixed-position LRC parser silently skips malformed lines, ignores offset metadata and cannot handle arbitrary LRC/TTML. Wrong song match is possible when artist/title/duration are ambiguous.

## Recommended order

1. Instrument/reproduce 30-second issue without collecting signed URLs; add byte-range regression tests.
2. Add deterministic provider, queue, migration and device background tests; improve sanitized error reporting.
3. Scope HTTP headers, review privacy/backup and car-browser authorization, then establish proper release signing/SBOM.
4. Review provider compatibility, dependency upgrades and source ownership in separate changes with regression testing. Do not infer NewPipe exists in this branch.
