# InnerTubeX v0.11.6 compatibility assessment

## Decision: NO-GO for application integration

The actual JitPack artifact has **not** been downloaded, its POM/Gradle module
metadata has **not** been resolved, and the prototype has **not** compiled
against it. BotGuard and Media3 integration must wait. Earlier four Kotlin tests
were executed against stand-ins and do not establish binary compatibility.
No production provider, Gradle configuration, minSdk, playback or lyrics changes
were made in this assessment.

Direct requests to JitPack, the Gradle distribution host and Google's Android SDK
repository failed with an SSL connection error in this environment. The reference
clone did not survive the prior workspace snapshot; it was re-cloned cleanly at
`/home/user/references/vivi-music`, detached at
`ed8fe35442f69d401096c5368cf808778285c0b2`. That external clone may disappear
again; the commit is the reproducible reference.

## What the source and upstream CI do show

* [Vivi v7.0.1](https://github.com/vivizzz007/vivi-music/tree/ed8fe35442f69d401096c5368cf808778285c0b2)
  specifies `com.github.MetrolistGroup.innertubex:innertubex-android:v0.11.6`,
  AGP 9.1.1, Kotlin 2.3.10, Ktor 3.4.0, Gradle 9.3.1, JDK 21, compile/targetSdk
  37 and minSdk 26. Its [FOSS release job](https://github.com/vivizzz007/vivi-music/actions/runs/42620785189/job/129172166562)
  reports a successful `assembleUniversalFossRelease` at that commit. This is
  independent evidence that the upstream app built, not a resolution report or
  proof that this prototype builds; the job logs and effective dependency graph
  were unavailable here.
* [InnerTubeX v0.11.6 source](https://github.com/MetrolistGroup/innertubex/tree/64c7df4d88d27e6964157b3b341f7ec70f88ea6e)
  declares AGP 9.4.1, Kotlin 2.4.21, Ktor 3.6.0, coroutines 1.11.0,
  serialization 1.12.0, compileSdk 37, minSdk 23 and a JDK 21 build toolchain.
  Its source declares API dependencies on Ktor core, kotlinx serialization JSON
  and coroutines, and an implementation dependency on QuackJS 0.1.4. These are
  source declarations, **not** the resolved published artifact's dependency tree.
  QuackJS source lists QuickJS as a JVM **test** dependency; do not assert it is
  a required production transitive dependency without the published graph.
* The upstream InnerTubeX Android build job succeeded in
  [CI](https://github.com/MetrolistGroup/innertubex/actions/runs/42330943106)
  at the pinned source commit; the separate iOS job in the same run failed. This
  does not test the artifact as consumed by our prototype.

## Risks and proposed resolutions — not yet verified

| Area | Existing ViMusic | Vivi / InnerTubeX | Proposed verification or resolution |
|---|---|---|---|
| Kotlin / Compose | Kotlin 1.7.20; Compose compiler 1.3.2 | Vivi 2.3.10; InnerTubeX built with 2.4.21 | Test artifact metadata and compile; migrate Kotlin and Compose compiler together if required. Do not suppress metadata checks. |
| Ktor | 2.1.2 | Vivi explicitly uses 3.4.0; source declares 3.6.0 | Inspect actual `debugRuntimeClasspath` and `dependencyInsight`. Upgrading the shared graph may break ViMusic's metadata/search and KuGou clients; port and regression-test those clients. |
| Coroutines / serialization | Coroutines 1.6.4; old serialization/toolchain | Source declares coroutines 1.11.0, serialization 1.12.0 | Resolve actual versions and run request-construction and search tests after any migration. |
| Gradle / AGP / JDK | Gradle 7.4, AGP 7.3.0, CI JDK 11 | Vivi Gradle 9.3.1, AGP 9.1.1, JDK 21 | Run isolated build first; then update the main multi-module build deliberately, including Kotlin, Compose, Room/kapt, scripts and CI. Do not assume a version matrix is compatible. |
| Android SDK | App min 21, compile/target 33; Compose libraries min 21 | Vivi min 26, compile/target 37; InnerTubeX source min 23 | User approved min 26; test all module manifests, Android resources and API levels. CompileSdk and targetSdk changes are separate from the minSdk choice. |
| Cipher transitive | No QuackJS in current app | InnerTubeX source declares QuackJS 0.1.4 | Verify actual AAR/module metadata, licenses, R8 and runtime behavior. |

The prototype declares Vivi's Ktor 3.4.0 explicitly. If the published artifact
also declares 3.6.0, Gradle may select a different version; only the real
resolution report can establish what was selected. Likewise, upstream source
versions do not by themselves prove the published Android artifact's minimum SDK
or JVM bytecode target. The isolated prototype is not included in root settings.

## Required next verification in a network-enabled environment

With JDK 21, Gradle 9.3.1, Android SDK platform 37 and access to Google Maven,
Maven Central and JitPack, run from the repository root:

```sh
gradle -p experiments/innertubex-compat --no-daemon --console=plain :adapter:assembleDebug :adapter:testDebugUnitTest
gradle -p experiments/innertubex-compat --no-daemon --console=plain :adapter:dependencies --configuration debugRuntimeClasspath
gradle -p experiments/innertubex-compat --no-daemon --console=plain :adapter:dependencyInsight --configuration debugRuntimeClasspath --dependency ktor-client-core
gradle -p experiments/innertubex-compat --no-daemon --console=plain :adapter:dependencyInsight --configuration debugRuntimeClasspath --dependency kotlinx-coroutines-core
gradle -p experiments/innertubex-compat --no-daemon --console=plain :adapter:dependencyInsight --configuration debugRuntimeClasspath --dependency kotlinx-serialization-json
gradle -p experiments/innertubex-compat --no-daemon --console=plain :adapter:dependencyInsight --configuration debugRuntimeClasspath --dependency quackjs
```

Record selected dependency versions, variants, artifact hashes/manifest minSdk,
compilation and individual test results. Sanitize logs before sharing: no API
keys, cookies, signed URLs, tokens or account information. Existing workflow
`.github/workflows/innertubex-compat.yml` would run after an appropriate future
push, but no push is authorized; GitHub `workflow_dispatch` also requires that
workflow to be present on the default branch. A source checkout of Vivi that
builds is not a substitute for compiling this prototype against the actual AAR.

After successful real-artifact validation, do BotGuard and Media3 in separate
stages, with bounded-range transport and expiry/403/416 tests. TTML remains an
independent task; preserve line and word timing and existing lyrics data. Real
device tests must prove tracks play past 30 seconds and through completion.

## Recoverability and exact file inventory

The session is fixed to branch `arena/aa6bae22-vimusic`; no separate branch was
created. Local HEAD remains `6e83b8b83da13b0c275702a0d6d5bff78e24c07a`; the
remote branch was observed at `fc9f10c56d5b34a57a3a4b84c7cf1339e042595c`.
A GitHub tree/content-hash comparison found all 43 pre-prototype modified or
untracked files have byte-for-byte equivalents in the remote commit. This is a
content comparison, not proof about modes, ignored files, or future remote
retention. The 16 prototype-stage files below are not in that remote commit.
They remain in the workspace snapshot, not in Git history.

A private recovery archive `recovery/vimusic-worktree-snapshot.tar.gz` records
all current tracked modifications and non-ignored untracked files, including the
prototype and this report. Its internal `MANIFEST.json` records file hashes,
modes, local base and observed remote commit. It was verified after creation.
It does not include `.git`, ignored files, app databases, user device data or any
future edits. Treat the archive as source-code-sensitive and keep it out of Git.
To inspect or recover, first extract it **outside** the working tree and compare
files; do not overwrite current edits without reviewing them. Arena's automatic
workspace snapshots are an additional convenience, not a Git commit or a substitute
for an independently saved backup.

### Tracked files modified before the prototype

- `.github/workflows/android.yml`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/service/PlayerService.kt`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/ui/screens/player/PlaybackError.kt`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/ui/screens/player/Thumbnail.kt`
- `innertube/build.gradle.kts`
- `innertube/src/main/kotlin/it/vfsfitvnm/innertube/Innertube.kt`
- `innertube/src/main/kotlin/it/vfsfitvnm/innertube/requests/Player.kt`
- `settings.gradle.kts`

### Earlier untracked files present in the remote commit by content

- `.github/scripts/gradle-ci.sh`
- `.github/scripts/install-gradle-ci.sh`
- `.github/scripts/run-emulator-tests.sh`
- `app/src/androidTest/assets/README.txt`
- `app/src/androidTest/assets/playback-regression.m4a`
- `app/src/androidTest/kotlin/it/vfsfitvnm/vimusic/service/PlaybackRegressionTest.kt`
- `app/src/androidTest/kotlin/it/vfsfitvnm/vimusic/service/RangeTestServer.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/res/xml/debug_network_security_config.xml`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/service/PlaybackDataSpecResolver.kt`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/service/PlaybackErrorDetails.kt`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/service/PlaybackLoadErrorHandlingPolicy.kt`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/service/PlaybackMedia3Log.kt`
- `app/src/main/kotlin/it/vfsfitvnm/vimusic/service/PlaybackNetworkDiagnostics.kt`
- `docs/API_INTEGRATIONS.md`
- `docs/APP_FLOW.md`
- `docs/ARCHITECTURE.md`
- `docs/BUILD_AND_TESTING.md`
- `docs/CHANGELOG.md`
- `docs/DATABASE.md`
- `docs/DEPENDENCIES.md`
- `docs/DEVELOPMENT_GUIDE.md`
- `docs/INNERTUBE_REQUEST_AUDIT.md`
- `docs/KNOWN_ISSUES.md`
- `docs/LYRICS_SYSTEM.md`
- `docs/PLAYBACK_ENGINE.md`
- `docs/PLAYBACK_HTTP_INCIDENT.md`
- `docs/README.md`
- `docs/SECURITY_AND_PRIVACY.md`
- `innertube/src/main/kotlin/it/vfsfitvnm/innertube/requests/PlayerRequestException.kt`
- `innertube/src/test/kotlin/it/vfsfitvnm/innertube/requests/LivePlayerProbeTest.kt`
- `innertube/src/test/kotlin/it/vfsfitvnm/innertube/requests/PlayerConstructionTest.kt`
- `innertube/src/test/kotlin/it/vfsfitvnm/innertube/requests/PlayerRequestTest.kt`

### Files created by the isolated prototype stage

- `.github/workflows/innertubex-compat.yml`
- `docs/VIVI_PLAYBACK_MIGRATION.md`
- `experiments/innertubex-compat/README.md`
- `experiments/innertubex-compat/adapter/build.gradle.kts`
- `experiments/innertubex-compat/adapter/src/main/AndroidManifest.xml`
- `experiments/innertubex-compat/adapter/src/main/kotlin/it/vfsfitvnm/vimusic/prototype/PlaybackAdapter.kt`
- `experiments/innertubex-compat/adapter/src/main/kotlin/it/vfsfitvnm/vimusic/prototype/StreamSnapshot.kt`
- `experiments/innertubex-compat/adapter/src/test/kotlin/it/vfsfitvnm/vimusic/prototype/PlaybackAdapterTest.kt`
- `experiments/innertubex-compat/adapter/src/test/kotlin/it/vfsfitvnm/vimusic/prototype/StreamSnapshotTest.kt`
- `experiments/innertubex-compat/build.gradle.kts`
- `experiments/innertubex-compat/gradle.properties`
- `experiments/innertubex-compat/settings.gradle.kts`
- `experiments/innertubex-compat/standalone/ExtractedStreamStub.kt`
- `experiments/innertubex-compat/standalone/JunitStub.kt`
- `experiments/innertubex-compat/standalone/RunSnapshotTests.kt`
- `experiments/innertubex-compat/standalone/run.sh`

### Files created for this compatibility assessment

- `docs/INNERTUBEX_COMPATIBILITY_REPORT.md` (this report)
- `recovery/vimusic-worktree-snapshot.tar.gz` (untracked private recovery snapshot; not included recursively in itself)
