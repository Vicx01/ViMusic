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

Tasks are suggested workflows, not claims that tests exist or passed. [`android.yml`](../.github/workflows/android.yml) runs on push on Ubuntu, installs Temurin JDK 11, builds `assembleDebug`, uploads the debug APK; it does not run tests explicitly. Gradle wrapper distribution URL points to `services.gradle.org`, so restricted build environments need access to the distribution and repositories. No dependency lockfiles, dependency verification metadata or distribution SHA-256 configured; reproducibility and supply-chain verification are incomplete.

## Executed in this audit

On the audit environment, ran `./gradlew :innertube:test :kugou:test :app:assembleDebug`; it exited **1 before Gradle started**: `JAVA_HOME is not set and no 'java' command could be found`. `java -version` also failed; `ANDROID_HOME` and `ANDROID_SDK_ROOT` were unset. No test, compilation, APK, emulator, live API request or device playback was executed. Network access is restricted in this environment and a local SDK was not established. Do not interpret this as a source compilation failure or a pass.

## Existing tests and practical plan

`innertube/src/test/kotlin/Test.kt` and `kugou/src/test/kotlin/Test.kt` contain empty JUnit test bodies; no meaningful unit, instrumentation, UI, fake network, migration or coverage configuration was found. Recommended staged suite:

1. Deterministic fixtures and Ktor mock engine: search filters, continuation, metadata renderer changes, player statuses, absent/cipher URLs, malformed and HTTP-error responses; assert no secret headers leak to other origins.
2. Fake byte-range HTTP server plus Media3 instrumentation: long-duration playback beyond first 512 KiB and several minutes, 200/206/403/416, truncated content, URL expiry/refresh, track skip/seek and full cause propagation. Assert headers and byte ranges without recording signed URLs.
3. Unit tests for playlist reorder/duplicate semantics, radio continuation/queue transitions, preference state and error mapping. Room instrumentation using exported schemas tests every supported migration, cascade, backup/restore and corrupt input.
4. Lyrics: fixtures for artist/title matching, ±5 s tolerance, missing results, malformed LRC, timestamp boundaries, seek/pause and sync scrolling. Add TTML/word timing only if designed later.
5. Device/emulator matrix for background/foreground, focus loss, headphones/Bluetooth, Android Auto opt-in, notification/lockscreen, abrupt process death, network loss/recovery, cache eviction and offline complete/partial playback. Test API 21 and supported modern versions.

Run offline deterministic tests in CI; keep live provider smoke tests opt-in, rate-limited and separate because services are unofficial and mutable. Cover user consent/privacy and external-service terms.
