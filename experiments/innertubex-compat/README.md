# InnerTubeX compatibility prototype

This is an **independent experimental Gradle project**, not included in the main
app's settings and not a playback fix. It uses Vivi v7.0.1's dependency choices
(InnerTubeX v0.11.6, AGP 9.1.1, Kotlin 2.3.10, Ktor 3.4.0, minSdk 26) to test
whether the Android artifact and selected APIs compile together. Gradle's actual
resolution may select newer transitive versions: inspect the dependency report.

Use JDK 21, Android SDK platform 37 and Gradle 9.3.1. From the repository root:

```sh
gradle -p experiments/innertubex-compat :adapter:assembleDebug :adapter:testDebugUnitTest
gradle -p experiments/innertubex-compat :adapter:dependencies --configuration debugRuntimeClasspath
```

The main project's Gradle 7.4 wrapper is **not** compatible with this experiment.
For a narrower offline check of the actual stream snapshot/cache logic with
stand-in InnerTubeX/JUnit types and Kotlin 2.4.21, use:

```sh
JAVA_HOME=/path/to/jdk21 KOTLINC=/path/to/kotlinc standalone/run.sh
```

Run that command from `experiments/innertubex-compat`. The stand-in tests do not
prove real artifact, Android, or Gradle compatibility; run the real Gradle/JUnit
tasks above as well. No live request is made by either set of tests.
`PlaybackAdapter.resolve` is an explicit network operation and is not invoked
by the tests. A real Android PoToken provider is
not implemented here: a caller must inject a `TokenProvider`. Bounded streams are
disabled pending a tested chunking DataSource. No files, dependencies, minimum
SDKs, credentials, or user data in the production app are changed by this project.

Sources inspected: Vivi v7.0.1 `ed8fe35442f69d401096c5368cf808778285c0b2`;
InnerTubeX v0.11.6 `64c7df4d88d27e6964157b3b341f7ec70f88ea6e`. Its dependency
uses QuackJS v0.1.4; review that and all resolved artifacts and their licenses
before integration. The prototype relies on the dependency API and does not copy
Vivi's token code or assets. See `docs/VIVI_PLAYBACK_MIGRATION.md` for status.
