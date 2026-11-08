# Build and testing

## Requirements and variants

* Android SDK platform 33 and compatible build tools; min API 21, compile/target API 33. Configure SDK via `ANDROID_HOME` or `sdk.dir` in `local.properties` (ignored). JDK 11 is used by CI; Kotlin/Java output target 1.8 with core-library desugaring.
* Gradle wrapper 7.4, AGP 7.3.0, Kotlin 1.7.20, Compose compiler 1.3.2. `settings.gradle.kts` enables type-safe accessors and sets Google Maven, Maven Central, JitPack. Root build uses Google, Maven Central and Gradle Plugin Portal. `gradle.properties` sets AndroidX, disables Jetifier, and enables full R8. Optional `-PenableComposeCompilerReports=true` generates compiler metrics in module build folders.
* Base app ID/namespace `it.vfsfitvnm.vimusic`, version 0.5.4 (20). Debug suffix `.debug`, label Debug. Release label ViMusic, R8/resource shrinking, **debug signing**. ABI splits are configured with universal APK enabled but no explicit ABI inclusions; confirm generated artifacts on a build. No flavors declared.

```sh
./gradlew assembleDebug
./gradlew :innertube:test :kugou:test
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest  # requires emulator/device
```

[`android.yml`](../.github/workflows/android.yml) runs on push on Ubuntu 24.04. It builds and uploads a debug APK, runs the JVM test tasks, compiles the instrumentation APK, and runs connected Android tests on an API 29 x86_64 emulator. Gradle uses JDK 11; Android SDK tools use JDK 17 in the emulator job. It checks that the six playback regression cases and three substantive resolver JVM cases appear in their test reports. CI obtains the Gradle 7.4 distribution with retries and verifies the published SHA-256, then shares the verified archive with the emulator job for one day. The normal Gradle wrapper still points to `services.gradle.org`; restricted build environments need access to the distribution and repositories. No dependency lockfiles or dependency verification metadata are configured; reproducibility and supply-chain verification remain incomplete.

## Validation results

GitHub Actions [run 42466547585](https://github.com/Vicx01/ViMusic/actions/runs/42466547585) at commit `7e8b736` built `:app:assembleDebug` and `:app:assembleDebugAndroidTest`, ran `:innertube:test`, `:kugou:test`, `:app:testDebugUnitTest`, and passed `:app:connectedDebugAndroidTest` on an API 29 x86_64 emulator. The JVM report contained four tests, including three substantive resolver tests (all passed); the emulator report contained six distinct tests, all passed. These use mocked responses, a local synthetic stream and a real Media3 loader, not live provider streams or physical devices. The debug APK SHA-256 is `288848bd7b6d52f34eff0b24bcfbbe580e83c312e17757ed2d1a42442136e3e3`. The build job uploads `vimusic-debug-apk` (a ZIP containing `app-debug.apk`) and the test APK; the emulator job uploads test results. Artifacts require GitHub access and expire according to repository retention policy.

Install the debug build after extracting it: `adb install -r app-debug.apk`. Its ID is `it.vfsfitvnm.vimusic.debug`, so it can coexist with the release app and has separate data. It uses a debug signing key and is for testing, not production distribution. Run `adb logcat -s PlaybackHttp:D PlaybackError:E` for sanitized diagnostics.

## Earlier local audit attempts

On the audit environment, ran `./gradlew :innertube:test :kugou:test :app:assembleDebug`; it exited **1 before Gradle started**: `JAVA_HOME is not set and no 'java' command could be found`. `java -version` also failed; `ANDROID_HOME` and `ANDROID_SDK_ROOT` were unset. A later attempt including `:app:testDebugUnitTest`, `:app:assembleDebugAndroidTest`, and `:app:connectedDebugAndroidTest` initially failed at the same Java check. Retried with a temporary JDK 11 in an isolated environment; the wrapper failed with an SSL handshake error while downloading Gradle from `services.gradle.org`. No Android SDK was configured. No Gradle test, app compilation, APK, emulator, live API request or device playback was executed. FFmpeg independently decoded the synthetic fixture to 50.03 seconds. The new Kotlin files, player UI Kotlin files, Gradle script, and debug XML were syntax-parsed; `PlayerService.kt` includes a pre-existing context receiver unsupported by that parser. This is not compilation or Android resource validation. A standalone JVM experiment with tagged Media3 `DataSpec`/`ResolvingDataSource`/`ByteArrayDataSource` and minimal Android stubs confirmed the old range transform reads 524,288 of 819,200 bytes versus all 819,200 for URI-only rewriting; this is not an Android app, cache, HTTP or player test. Network access is restricted in this environment and a local SDK was not established. Do not interpret this as a source compilation failure or a pass.

## Existing tests and practical plan

`innertube/src/test/kotlin/Test.kt` and `kugou/src/test/kotlin/Test.kt` contain empty JUnit test bodies, while `innertube` now also has substantive mocked player-request tests. `app/src/androidTest` contains resolver, HTTP range/header, loader forwarding, error-redaction and 37-second playback regression tests using a local server and synthetic audio; these ran on the API 29 emulator in the CI run above. No migration, broad UI or coverage configuration was found. The local server requires the debug-only 127.0.0.1 cleartext exception. Recommended staged suite:

1. Deterministic fixtures and Ktor mock engine: search filters, continuation, metadata renderer changes, player statuses, absent/cipher URLs, malformed and HTTP-error responses; assert no secret headers leak to other origins.
2. Extend the added fake byte-range HTTP server and Media3 instrumentation with: long-duration playback beyond first 512 KiB and several minutes, 200/206/403/416, truncated content, URL expiry/refresh, track skip/seek and full cause propagation. Assert headers and byte ranges without recording signed URLs.
3. Unit tests for playlist reorder/duplicate semantics, radio continuation/queue transitions, preference state and error mapping. Room instrumentation using exported schemas tests every supported migration, cascade, backup/restore and corrupt input.
4. Lyrics: fixtures for artist/title matching, ±5 s tolerance, missing results, malformed LRC, timestamp boundaries, seek/pause and sync scrolling. Add TTML/word timing only if designed later.
5. Device/emulator matrix for background/foreground, focus loss, headphones/Bluetooth, Android Auto opt-in, notification/lockscreen, abrupt process death, network loss/recovery, cache eviction and offline complete/partial playback. Test API 21 and supported modern versions.

Run offline deterministic tests in CI; keep live provider smoke tests opt-in, rate-limited and separate because services are unofficial and mutable. Cover user consent/privacy and external-service terms.
