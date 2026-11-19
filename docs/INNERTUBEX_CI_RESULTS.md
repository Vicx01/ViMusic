# InnerTubeX compatibility CI results

## Verified result

GitHub Actions [run 44559276210](https://github.com/Vicx01/ViMusic/actions/runs/44559276210), commit `f582c2ece818fceed8fa42ca86c1c466817d4c17`, passed both jobs:

* **Real artifact:** resolved `com.github.MetrolistGroup.innertubex:innertubex-android:v0.11.6`, compiled the isolated Android library with `:probe:assembleDebug`, and ran the single real-artifact JVM unit test using `:probe:testDebugUnitTest`. The expected test was verified as executed and passing.
* **Playback prototype:** compiled the separate `:adapter:assembleDebug` project against the real dependency, ran its offline `:adapter:testDebugUnitTest` task, and verified five test cases executed with no failures.

The isolated projects used JDK 21, Gradle 9.3.1, AGP 9.1.1, Kotlin plugin 2.3.10, SDK platform 37.0 and minSdk 26. This verifies the isolated builds, not the production ViMusic dependency graph, BotGuard, a live provider, Android device playback, or a playback fix.

## Actual resolved production runtime graph (selected modules)

The real-artifact job's sanitized check annotation recorded:

| Component | Selected version |
|---|---|
| `innertubex-android` | `v0.11.6` |
| `quackjs-android` | `0.1.4` |
| Ktor JVM modules, including `ktor-client-core-jvm` | `3.6.0` |
| `kotlin-stdlib` | `2.4.21` |
| `kotlinx-coroutines-core-jvm` | `1.11.0` |
| `kotlinx-coroutines-slf4j` | `1.11.0` |
| `kotlinx-serialization-core-jvm` / `json-jvm` | `1.12.0` |
| `kotlinx-io-core-jvm` / `bytestring-jvm` | `0.9.1` |

The full compile, runtime and unit-test dependency trees, insights, runtime artifact names and SHA-256 hashes, and JUnit XML are in [innertubex-artifact-check-reports](https://github.com/Vicx01/ViMusic/actions/runs/44559276210/artifacts/14548725891). The prototype runtime tree and JUnit XML are in [innertubex-playback-prototype-reports](https://github.com/Vicx01/ViMusic/actions/runs/44559276210/artifacts/14548195101). GitHub reports expiration on 2027-02-17; availability follows repository artifact retention. Hashes record identities but were not compared with independently trusted checksums. Sandbox restrictions prevented downloading and inspecting the ZIPs; the versions and pass counts above come from successful CI check annotations and job metadata.

## Production compatibility decision

**Proceed to a separately tested dependency migration, not direct production integration yet.** ViMusic currently declares Kotlin 1.7.20, Ktor 2.1.2, coroutines 1.6.4, AGP 7.3.0, Gradle 7.4 and Media3 1.0.0-beta03. The isolated host compiled with Kotlin plugin 2.3.10 while resolving stdlib 2.4.21, but that does not establish that ViMusic's Compose compiler, Room/kapt, Ktor 2 APIs or other modules work with that graph. Measure the actual whole-app dependency graph and upgrade/port affected modules with regression tests before connecting the provider. Do not silently force older transitive versions or suppress metadata compatibility checks.

## Existing app regression baseline

The independent existing [ViMusic CI run 44559275028](https://github.com/Vicx01/ViMusic/actions/runs/44559275028) at the same commit passed the debug build, JVM tasks and API 29 instrumentation. Its annotations reported six JVM tests and six instrumentation tests with no failures; these are synthetic/offline tests. This build used the existing playback provider, not InnerTubeX. It is not proof of live YouTube playback.

Both workflow runs reported GitHub warnings about action/Node version deprecation; the warnings did not prevent these builds. Prior compatibility runs [44558480566](https://github.com/Vicx01/ViMusic/actions/runs/44558480566), [44558815435](https://github.com/Vicx01/ViMusic/actions/runs/44558815435) and [44558979481](https://github.com/Vicx01/ViMusic/actions/runs/44558979481) failed before compilation because of SDK setup/package naming or the initial test-runtime artifact reporting task. The successful run fixes those gating problems.
