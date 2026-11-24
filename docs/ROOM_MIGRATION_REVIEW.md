# Focused Room migration and converter review

## Production safety boundary

This review changes no production database class, converter body, schema, migration or user data. It adds test-only Room schema assets and the matching `room-testing` dependency to the Android instrumentation APK. Production remains on Kotlin 1.8.22, Compose compiler 1.4.8, Ktor 2.3.12, Room 2.5.0-beta01, AGP 7.3.0 and Gradle 7.4. All test databases are uniquely named and never use `data.db`.

## Migration inventory

Schemas 1 through 23 are checked in under `app/schemas/it.vfsfitvnm.vimusic.DatabaseInitializer`. The current database has version 23. Adjacent migration paths are:

* **Generated auto-migrations:** 1→2, 2→3, 3→4, 4→5, 5→6, 6→7, 7→8, 9→10, 11→12, 12→13, 13→14, 15→16, 16→17, 17→18, 18→19, 19→20, 20→21 and 21→22. The 3→4, 7→8, 11→12, 20→21 and 21→22 paths have explicit auto-migration specs.
* **Manual migrations:** 8→9 changes artist/album relations; 10→11 moves album relations and rebuilds `Song`; 14→15 moves format data and rebuilds `Song`; 22→23 moves fixed/synchronized lyrics into `Lyrics` and rebuilds `Song`.

The existing 3→4 spec explicitly deletes `QueuedMediaItem`; 4→5 has no queue table and 5→6 creates it again. Therefore queued records present in schema 3 are not retained by that migration as written. This is an existing migration behavior, not caused by the proposed `@JvmStatic` candidate. Tests must report that loss rather than silently call it a pass. Other historical drop-table/rebuild and foreign-key effects require actual tests; do not infer their outcome from source inspection.

## Tests

`DatabaseMigrationTest` uses `MigrationTestHelper` and the actual exported old schemas to exercise each adjacent 1→2 through 22→23 path, plus complete paths from every prior version to 23. It seeds synthetic song, playlist, mapping, lyrics (where present), and queue fixtures including null position/title; it validates schema and retained values. For queue items it independently constructs the historical Parcel/Bundle shape without calling the production encoder and deserializes it with the existing converter. The migration 3→4 queue deletion is deliberately treated as a preservation failure. This test does not yet prove compatibility with blobs emitted by older Media3 binaries or all combinations of historical user data. Results must be read from CI; source code alone is not a pass.

The existing persistence test covers a current-schema Room write, close/reopen/read, and null converter inputs. The isolated candidate CI applies `@JvmStatic` only in runner copies, and exercises the candidate with both Kotlin 1.8.22 and 1.9.25 on API 26 and 29. Current production remains untouched.

## Processor candidates

The diagnostic exports the full sanitized KAPT dependency tree, generated Room implementation and converter disassembly for each isolated case. In addition to prior comparisons, it tries Room 2.6.1 with compileSdk 34 in a runner copy, and compares generated Java before and after static annotations under the same Kotlin 1.8.22 compiler. These are experiments, not recommended production upgrades; actual build outcomes and migration tests are required before a decision. A forced metadata-reader replacement previously failed inside KAPT. Room 2.5.2 still generated the invalid constructor with Kotlin 1.9. Room 2.6.1 requires at least compileSdk 34 and its behavior is being tested separately.

## Infrastructure

The previous [API-29 CI failure](https://github.com/Vicx01/ViMusic/actions/runs/45210724235) was a Maven Central HTTP 429 fetching a test-platform dependency. A GitHub failed-job rerun returned integration HTTP 403. A fresh run via a branch push is the alternative route; no sandbox download retries are used. Track its actual outcome rather than classifying either infrastructure response as a code regression.
