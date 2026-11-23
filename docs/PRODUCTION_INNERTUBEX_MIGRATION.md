# Production InnerTubeX migration

## Baseline and first checkpoint

The [isolated compatibility run](https://github.com/Vicx01/ViMusic/actions/runs/44559276210) resolved and compiled InnerTubeX v0.11.6 and passed its artifact and adapter tests. It did not build ViMusic with that dependency or exercise live playback. The detailed isolated results are in [INNERTUBEX_CI_RESULTS.md](https://github.com/Vicx01/ViMusic/blob/44453445fb15fd669938e7d6dec2fff2c3f10e57/docs/INNERTUBEX_CI_RESULTS.md).

The first production checkpoint raises only the app's `defaultConfig.minSdk` from 21 to 26 (Android 8.0). This intentionally ends support for devices below API 26. There are no product flavors or per-build-type minSdk overrides; the setting applies to both debug and release. The three Compose Android libraries still declare 21: they may support an older minimum than their consuming app and need not change just to raise the app minimum. The remaining existing modules are Kotlin/JVM modules. No data, database schema, app ID, provider, target SDK, compile SDK or playback code changes in this checkpoint. CI verifies the packaged debug APK actually declares API 26, then runs the existing JVM and API 29 emulator suites. API 26 and release-device behavior are not established by that emulator run.

## Toolchain evidence and API 26 gate

The [InnerTubeX v0.11.6 source catalog](https://github.com/MetrolistGroup/innertubex/blob/64c7df4d88d27e6964157b3b341f7ec70f88ea6e/gradle/libs.versions.toml) declares Kotlin **2.4.21**, AGP **9.4.1**, Ktor **3.6.0**, and compileSdk **37**; its [wrapper](https://github.com/MetrolistGroup/innertubex/blob/64c7df4d88d27e6964157b3b341f7ec70f88ea6e/gradle/wrapper/gradle-wrapper.properties) declares Gradle **9.8.2**. Those are its producer build versions, not established minimum consumer versions. The real published artifact and prototype compiled in [run 44559276210](https://github.com/Vicx01/ViMusic/actions/runs/44559276210) with Kotlin plugin **2.3.10**, AGP **9.1.1**, Gradle **9.3.1**, JDK **21**, and SDK 37. This is one demonstrated compatible isolated consumer toolchain; the oldest compatible compiler, AGP and Gradle versions have not been established, and whole-app compatibility still requires testing.

The existing debug APK is now explicitly installed on API 26 and API 29 emulators before the instrumentation suite runs. The test jobs verify the actual emulator API and use distinct report artifacts. Emulator tests do not replace a physical-device playback test. API 26 CI execution is pending until its run completes.

## Dependency and build boundaries

| Area | Current production declarations | Isolated successful consumer | Migration concern |
|---|---|---|---|
| Gradle / AGP / JDK | Gradle 7.4 / AGP 7.3.0 / JDK 11 | 9.3.1 / 9.1.1 / JDK 21 | AGP 9 has different Kotlin and Android DSL behavior; existing scripts, CI Gradle verification, kapt and Android plugins need an explicit port. |
| Kotlin / Compose | Kotlin 1.7.20; Compose compiler 1.3.2 | Kotlin plugin 2.3.10; selected Kotlin stdlib 2.4.21 | Existing Compose compiler is tied to the older Kotlin setup. Test the Compose compiler plugin and all Compose modules with any proposed Kotlin upgrade; the isolated pass does not prove whole-app compatibility. |
| Ktor | 2.1.2 | Resolved 3.6.0 | `:innertube`, `:kugou`, and `:ktor-client-brotli` use Ktor APIs. One app runtime selects shared dependencies, so adding a separate Gradle module alone does not isolate Ktor 2 from Ktor 3. Port and test the existing clients before enabling the new artifact. |
| Coroutines / serialization | coroutines 1.6.4 and Kotlin 1.7.20 serialization plugin | coroutines 1.11.0 and serialization runtime 1.12.0 selected | Update compiler plugin and runtime coherently; verify model deserialization, request construction, cancellation and lyrics/network flows. Do not force older transitive dependencies or suppress Kotlin metadata checks to obtain a green build. |
| Android / bytecode | app API 26 after this checkpoint, compile/target 33, Java/Kotlin bytecode 8 | compile 37, min 26, toolchain 21 | Determine required app and Android-library compile SDK and Java bytecode upgrades experimentally; don't raise target SDK implicitly. Check desugaring, manifest merge and supported devices. |
| Room / Media3 | Room 2.5.0-beta01 with kapt; Media3 1.0.0-beta03 | Not exercised by isolated gate | Preserve Room schemas and stored data; test existing migration paths before any Room change. Upgrade Media3 only if needed and verify cache, queue, seek and error-policy behavior. |

A candidate production dependency belongs in an Android playback module or the Android app, not in the existing JVM `:innertube` search/metadata module. Preserve the latter's search and metadata API. Introduce the new module behind an explicitly controlled provider boundary; do not ship it as the default until builds and behavioral tests pass. Runtime dependency alignment is still required across all modules. CI now captures sanitized whole-app debug compile, runtime and unit-test runtime dependency trees as an artifact, and summarizes selected Kotlin/Ktor modules in a check annotation. Collect fresh trees and dependency-insight reports after adding the artifact; the isolated artifact graph is not a substitute for an app graph.

## Playback and recovery boundaries

`PlayerService.createDataSourceFactory()` currently resolves video IDs through the legacy `Innertube.player` path, records metadata in the existing database, and wraps the existing cache upstream in a `ResolvingDataSource`. `PlaybackDataSpecResolver` keeps a two-entry URI ring, checks the media cache, and preserves the requested `DataSpec` range. The current URI-only boundary cannot carry InnerTubeX's request headers, expiry or bounded-range requirements. Simply shortening a `DataSpec` would report end-of-input rather than fetch the next chunk.

A migration needs a typed, redacted stream result carrying URL, required request headers, expiry, client/format identity and transport flags; an interface that keeps the legacy resolver selectable; lifecycle-owned InnerTubeX/cipher/client/token resources; and a Media3 bridge that propagates headers and uses stable video-ID cache keys. Preserve existing duration/format database updates and media-item/queue semantics. Never log URLs, headers, cookies, visitor data or tokens.

Use expiry-aware, session-scoped in-memory stream metadata and explicit invalidation. On HTTP 403, invalidate the affected stream and perform bounded refresh/retry with concurrency control; do not invalidate downloaded audio unnecessarily or loop indefinitely. A 416 can indicate end-of-input or stale/incorrect range information: distinguish those cases, preserve seek positions and recover only where justified. Test each case with a controlled HTTP server, concurrent refresh, account/session changes, expired entries, cache hits, bounded ranges, seeking, unknown length and EOF. Vivi is a reference, not proof that its recovery is safe to copy unchanged.

For BotGuard/PoToken, separately audit Vivi's HTML, JavaScript, WebView bridge, licenses, lifecycle and privacy before enabling network use. Scope WebView access and bridge exposure, dispose resources, and surface failures without disclosing token material. No token implementation or live provider behavior is claimed by this checkpoint.

## Gates and rollback

1. Pass the existing app CI after the API 26 checkpoint. Confirm the APK manifest and record any failure rather than assuming this checkpoint passes.
2. Upgrade build tooling, Kotlin/Compose and Ktor-related clients in testable increments, preserving legacy behavior and comparing whole-app graphs. Run the existing CI and relevant serialization/client tests after each significant step.
3. Add and test an opt-in InnerTubeX/BotGuard provider and then a Media3 bridge, retaining the existing provider. Verify stream expiry and bounded 403/416 recovery with synthetic tests before any switch.
4. Test actual YouTube songs on a physical Android device: continuous playback beyond 30 seconds, seeking, and full completion, including relevant failure recovery. Do not label playback fixed before this passes.
5. Handle Vivi-based TTML and lyric providers as a separate task, preserving line-level and word-level timing and existing user lyrics.

Each committed stage can be reverted on the same branch without rewriting history. Existing private recovery snapshots remain outside Git. A rollback to pre-API-26 builds also restores support for API 21–25; it does not require a database migration.
