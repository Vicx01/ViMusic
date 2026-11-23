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

The experiment records actual build status, selected KAPT graph lines, the generated implementation's converter references, and the KAPT stub's metadata version and signatures. Kotlin 1.9 cases use the corresponding Compose compiler 1.5.15. The exploratory cases are diagnostic observations, not production dependency upgrades or approval to change converter ABI. A green compile alone does not establish database safety or compatibility with InnerTubeX. Results remain pending until CI executes them.

`DatabasePersistenceTest` uses uniquely named test-only on-disk databases, writes and reads the existing queue entity through Room's actual converters after close/reopen, and checks null/direct converter round trips. It never opens or deletes `data.db`. CI requires these tests alongside the existing tests on API 26 and 29. These fixtures test current-schema persistence, not migration from every historical schema or existing real-device databases.

## Guidance and remaining gates

The [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) document a kotlinx-metadata-jvm incompatibility fix in Room 2.5.2. [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions) recommends preserving exported schema histories and testing migrations and retained data. These sources motivate controlled comparisons but do not, by themselves, prove this failure's cause. Verify generated output and test candidate changes before choosing a fix. Historical schema migration tests and cross-version persisted-data tests remain necessary before any production Room upgrade. The API 26/29 emulator tests are not physical-device playback tests.
