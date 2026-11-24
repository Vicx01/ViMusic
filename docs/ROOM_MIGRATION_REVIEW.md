# Focused Room migration and converter review

## Production safety boundary

This review changes no production database class, converter body, schema, migration or user data. It adds test-only Room schema assets and the matching `room-testing` dependency to the Android instrumentation APK. Production remains on Kotlin 1.8.22, Compose compiler 1.4.8, Ktor 2.3.12, Room 2.5.0-beta01, AGP 7.3.0 and Gradle 7.4. All test databases are uniquely named and never use `data.db`.

## Migration inventory

Schemas 1 through 23 are checked in under `app/schemas/it.vfsfitvnm.vimusic.DatabaseInitializer`. The current database has version 23. Adjacent migration paths are:

* **Generated auto-migrations:** 1→2, 2→3, 3→4, 4→5, 5→6, 6→7, 7→8, 9→10, 11→12, 12→13, 13→14, 15→16, 16→17, 17→18, 18→19, 19→20, 20→21 and 21→22. The 3→4, 7→8, 11→12, 20→21 and 21→22 paths have explicit auto-migration specs.
* **Manual migrations:** 8→9 changes artist/album relations; 10→11 moves album relations and rebuilds `Song`; 14→15 moves format data and rebuilds `Song`; 22→23 moves fixed/synchronized lyrics into `Lyrics` and rebuilds `Song`.

The existing 3→4 spec explicitly deletes `QueuedMediaItem`; 4→5 has no queue table and 5→6 creates it again. Therefore queued records present in schema 3 are not retained by that migration as written. This is an existing migration behavior, not caused by the proposed `@JvmStatic` candidate. Tests report that loss rather than silently calling it a pass. This review does not change historical migrations; resolving the known loss requires a separately authorized, non-destructive plan.

## Tests

`DatabaseMigrationTest` uses `MigrationTestHelper` and the actual exported old schemas to exercise each adjacent 1→2 through 22→23 path, plus complete paths from every prior version to 23. It seeds synthetic song, playlist, mapping, lyrics (where present), and queue fixtures including null position/title; it validates schema and retained values. For queue items it independently constructs the historical Parcel/Bundle shape without calling the production encoder and deserializes it with the existing converter. The migration 3→4 queue deletion is deliberately treated as a preservation failure. This test does not yet prove compatibility with blobs emitted by older Media3 binaries or all combinations of historical user data. Results must be read from CI; source code alone is not a pass.

The existing persistence test covers a current-schema Room write, close/reopen/read, and null converter inputs. The isolated candidate CI applies `@JvmStatic` only in runner copies, and exercises the candidate with both Kotlin 1.8.22 and 1.9.25 on API 26 and 29. Current production remains untouched.

**Observed results:** [production run 45356524371](https://github.com/Vicx01/ViMusic/actions/runs/45356524371) built successfully and installed on API 26 and 29. Both emulators executed 10 tests, and both migration tests failed because 3→4 removes the queue and paths 2→23 and 3→23 lose the queued rows. The other 21 adjacent and 20 full-path synthetic fixture cases completed without a reported failure; the eight pre-existing persistence/playback tests passed. [Candidate run 45356524439](https://github.com/Vicx01/ViMusic/actions/runs/45356524439) built on both Kotlin versions and showed the same two migration test failures and eight passing existing tests for all four Kotlin/API combinations. These are real, pre-existing migration/data-retention failures surfaced by the new tests, not a successful all-migrations compatibility result. The tests do not cover every field or data shape. The previous API-29 infrastructure failure was successfully bypassed by this fresh run, which reached and ran tests.

## Processor candidates

The diagnostic exports the full sanitized KAPT dependency tree, generated Room implementation and converter disassembly for each isolated case. In addition to prior comparisons, it tries Room 2.6.1 with compileSdk 34 in a runner copy and compares generated Java before and after static annotations under the same Kotlin 1.8.22 compiler. These are experiments, not production upgrades.

[Diagnostic 45356524364](https://github.com/Vicx01/ViMusic/actions/runs/45356524364): Room 2.6.1 plus compileSdk 34 and Kotlin 1.9 generated `Converters.INSTANCE` calls instead of the invalid constructor, but the build still failed in `compileDebugKotlin` (specific compiler errors not yet captured). It is not a proven working upgrade. Room 2.5.2 still generates the invalid constructor. Forcing metadata-jvm 0.6.2 into Room 2.5.0-beta01 produces `NoSuchMethodError` for `KotlinClassMetadata.Companion.read(KotlinClassHeader)`, an ABI incompatibility rather than a fix. Kotlin 1.8 static and Kotlin 1.9 static diagnostic builds succeeded, respectively dispatching through `Converters.INSTANCE` and static `Converters` calls. Kotlin 1.8 baseline diagnostic hit a Maven 429, so full-file comparison could not run on that attempt.

**Provisional recommendation:** the two converter annotations in isolation are smaller than upgrading Room/compileSdk and preserve the existing converter bodies. Do not apply them to production: historical queue retention fails and the older binary-produced blob gap remains. Kotlin 1.9 whole-app and provider compatibility are not established.

## Infrastructure

The previous [API-29 CI failure](https://github.com/Vicx01/ViMusic/actions/runs/45210724235) was a Maven Central HTTP 429 fetching a test-platform dependency. A GitHub failed-job rerun returned integration HTTP 403. A fresh run via a branch push is the alternative route; no sandbox download retries are used. Track its actual outcome rather than classifying either infrastructure response as a code regression.
