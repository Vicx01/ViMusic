# Room / Kotlin interoperability investigation

## Recovery baseline

The last passing production configuration is Kotlin/serialization plugin 1.8.22, Compose compiler 1.4.8, Ktor 2.3.12, coroutines 1.7.1, Room runtime and compiler 2.5.0-beta01, AGP 7.3.0 and Gradle 7.4. The restored [production CI run](https://github.com/Vicx01/ViMusic/actions/runs/45204365148) passed on API 26 and 29. The Kotlin 1.9.25 / Compose compiler 1.5.15 [build](https://github.com/Vicx01/ViMusic/actions/runs/45203656601) failed before tests: Room-generated `Database_Impl.java` tried to invoke the private constructor of `Converters`.

## Existing implementation

`DatabaseInitializer` registers `@TypeConverters(Converters::class)`. `Converters` is a Kotlin `object` with two instance methods annotated `@TypeConverter`, converting a Media3 `MediaItem` to and from a `Parcel`-backed byte array. `QueuedMediaItem.mediaItem` persists this representation in a BLOB. `:app` applies `kotlin("kapt")`, declares `kapt(libs.room.compiler)` and `implementation(libs.room)`; the shared catalog selects Room 2.5.0-beta01 for both runtime/ktx and compiler. KSP is not applied. No production database source, version, migrations, schemas, converters or stored format is changed by this investigation.

## Differential experiment

`.github/workflows/room-kotlin-diagnostic.yml` makes disposable runner copies of the same checkout and compiles five cases. It never alters the checkout or production `data.db`:

1. Kotlin 1.8.22 / Room 2.5.0-beta01 with the original object (control).
2. Kotlin 1.9.25 / Room 2.5.0-beta01 with the original object (reproduction).
3. Kotlin 1.9.25 / Room 2.5.2 with the original object.
4. Kotlin 1.9.25 / Room 2.6.1 with the original object.
5. Kotlin 1.9.25 / Room 2.5.0-beta01 with `@JvmStatic` added to the same methods **only in the runner copy**.
6. Kotlin 1.9.25 / Room 2.5.0-beta01 with KAPT's `kotlinx-metadata-jvm` experimentally changed from 0.5.0 to 0.6.2 **only in the runner copy**, to test the metadata-version hypothesis.

## Results and cause

The [diagnostic runs](https://github.com/Vicx01/ViMusic/actions/runs/45208755996) show:

| Disposable case | Observation |
|---|---|
| Kotlin 1.8.22 / Room 2.5.0-beta01 | Build and JVM tests passed. KAPT stub has metadata `1, 8, 0`, private constructor and `INSTANCE`; generated calls use `Converters.INSTANCE`. |
| Kotlin 1.9.25 / Room 2.5.0-beta01 | Build fails. Stub has metadata `1, 9, 0`, the same private constructor and `INSTANCE`; generated `Database_Impl.java` incorrectly contains `new Converters()`. The KAPT graph selects `kotlinx-metadata-jvm:0.5.0`. |
| Kotlin 1.9.25 / Room 2.5.2 | Same invalid constructor generation; KAPT graph still selects metadata 0.5.0. The 2.5.2 release-note metadata fix alone is not sufficient here. |
| Kotlin 1.9.25 / Room 2.6.1 | Fails before KAPT at `checkDebugAarMetadata`: Room runtime requires compileSdk 34+, while the app uses 33. It does not establish whether that Room version fixes this source on the existing toolchain. |
| Kotlin 1.9.25 / Room 2.5.0-beta01 / static methods | Build and JVM tests passed; stub methods are static, and generated code calls `Converters.mediaItemToByteArray` and `Converters.mediaItemFromByteArray` directly. |
| Kotlin 1.9.25 / Room 2.5.0-beta01 / KAPT metadata 0.6.2 | KAPT graph resolves 0.6.2, but `kaptDebugKotlin` fails with an `InvocationTargetException` before Room generates the implementation. The nested cause is not available in the sanitized report; this override is not a validated fix. |

The immediate root cause is Room's KAPT processor treating the Kotlin singleton as an ordinary class under Kotlin 1.9 and generating a call to its inaccessible constructor. The evidence identifies the compatibility boundary: the unchanged converter declaration works with Kotlin 1.8, but the older processor uses `kotlinx-metadata-jvm:0.5.0` and sees Kotlin 1.9 metadata. [Kotlin's metadata changelog](https://github.com/JetBrains/kotlin/blob/master/libraries/kotlinx-metadata/jvm/ChangeLog.md) documents 0.5.0 support through metadata 1.8 and 0.6.0 support for 1.9. A pre-release [AndroidX processor source snapshot](https://github.com/androidx/androidx/blob/155f7ec288a5b63a136cd2e0ebd177689cc7a9e7/room/room-compiler-processing/src/main/java/androidx/room/compiler/processing/javac/JavacTypeElement.kt) checks Kotlin object status through parsed metadata; its [metadata reader](https://github.com/androidx/androidx/blob/155f7ec288a5b63a136cd2e0ebd177689cc7a9e7/room/room-compiler-processing/src/main/java/androidx/room/compiler/processing/javac/kotlin/KotlinMetadataElement.kt) returns null when it cannot parse a Kotlin class, and the [converter processor](https://github.com/androidx/androidx/blob/155f7ec288a5b63a136cd2e0ebd177689cc7a9e7/room/room-compiler/src/main/kotlin/androidx/room/processor/CustomConverterProcessor.kt) branches on that object status. This aligns with the observed output and the library's supported metadata versions; the diagnostic does not directly instrument the binary reader's return value. KSP was not tested.

The narrow candidate is adding `@JvmStatic` to both existing converter methods, preserving their bodies, types, database schema and BLOB format. It is tested only in the runner copy. The [isolated full candidate CI](https://github.com/Vicx01/ViMusic/actions/runs/45208756318) passed the debug build, existing JVM tests, APK installation and all eight required instrumentation cases on API 26 and 29. That supports the candidate for the tested paths but does not prove all Room-generated code, older schema migrations, cross-version on-disk data or InnerTubeX compatibility. Do not treat the old metadata reader as generally fixed or introduce a production converter change until those risks are reviewed.

`DatabasePersistenceTest` uses uniquely named test-only on-disk databases, writes and reads the existing queue entity through Room's actual converters after close/reopen, and checks null/direct converter round trips. It never opens or deletes `data.db`. [Production baseline CI](https://github.com/Vicx01/ViMusic/actions/runs/45207090391) passed all eight required tests on both API 26 and 29. These fixtures test current-schema persistence, not migration from every historical schema or existing real-device databases.

## Guidance and remaining gates

The [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) document a kotlinx-metadata-jvm incompatibility fix in Room 2.5.2. [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions) recommends preserving exported schema histories and testing migrations and retained data. These sources motivate controlled comparisons but do not, by themselves, prove this failure's cause. Verify generated output and test candidate changes before choosing a fix. Historical schema migration tests and cross-version persisted-data tests remain necessary before any production Room upgrade. The API 26/29 emulator tests are not physical-device playback tests.
