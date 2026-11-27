# Real InnerTubeX artifact compatibility gate

This standalone Gradle project lives inside the repository but is not included
by the production root build. It depends on the **published**
`com.github.MetrolistGroup.innertubex:innertubex-android:v0.11.6` artifact;
JUnit is test-only. No ViMusic code, stubs, dependency substitutions, exclusions,
forced transitives, BotGuard, Media3, account credentials or live requests are
used. The companion CI workflow also builds the separate playback prototype.

It initially mirrors Vivi v7.0.1: Gradle 9.3.1, AGP 9.1.1, Kotlin 2.3.10,
JDK 21, compileSdk 37 and minSdk 26. Those are **hypotheses**, not proof: the
InnerTubeX source declares Kotlin 2.4.21 and Ktor 3.6.0. No explicit Ktor version
is declared here. A successful result would verify this isolated host and real
artifact, not ViMusic's old dependency graph or continuous playback.

Run on a machine with JDK 21, Gradle 9.3.1, Android SDK platform 37, and access
to Google Maven, Maven Central, the Gradle Plugin Portal and JitPack:

```sh
gradle -p experiments/innertubex-artifact-check :probe:writeResolvedArtifacts
gradle -p experiments/innertubex-artifact-check :probe:dependencies --configuration debugCompileClasspath
gradle -p experiments/innertubex-artifact-check :probe:dependencies --configuration debugRuntimeClasspath
gradle -p experiments/innertubex-artifact-check :probe:dependencies --configuration debugUnitTestRuntimeClasspath
gradle -p experiments/innertubex-artifact-check :probe:assembleDebug :probe:testDebugUnitTest
```

The CI workflow records compile/runtime/test dependency trees, insights and
artifact identities/SHA-256 hashes for the production runtime classpath. Hashes identify
the downloaded artifacts; they are not compared to independently trusted hashes.
No signed URLs, cookies, tokens, API keys or real song streams are needed. The
main repository's Gradle 7.4 wrapper must not be used for this project. Do not
claim a pass unless the actual artifact resolves, Kotlin compiles and the named
JUnit test executes; no such run has occurred as of this change.
