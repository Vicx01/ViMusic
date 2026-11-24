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

[Diagnostic 45358283623](https://github.com/Vicx01/ViMusic/actions/runs/45358283623) captured the exact [baseline KAPT dependency graph](ROOM_PROCESSOR_KAPT_GRAPH.txt). Its root is `room-compiler:2.5.0-beta01`, with `room-compiler-processing:2.5.0-beta01`, `room-migration:2.5.0-beta01`, and `kotlinx-metadata-jvm:0.5.0`; the compiler graph selects Kotlin stdlib 1.7.10, not the application's runtime stdlib. See the complete graph for every selected transitive dependency and conflict resolution.

Room 2.6.1 plus compileSdk 34 and Kotlin 1.9 generated `Converters.INSTANCE` calls instead of the invalid constructor, but the build failed in `compileDebugKotlin`: `PlayerService` no longer matches the API-34 `OnSharedPreferenceChangeListener` nullable key signature (`onSharedPreferenceChanged` overrides nothing). This build did not test runtime or migrations, and fixing unrelated code was outside this review. Room 2.5.2 still generates the invalid constructor. Forcing metadata-jvm 0.6.2 into Room 2.5.0-beta01 produces `NoSuchMethodError` for `KotlinClassMetadata.Companion.read(KotlinClassHeader)`, an ABI incompatibility rather than a fix.

Kotlin 1.8 static and Kotlin 1.9 static diagnostic builds succeeded, respectively dispatching through `Converters.INSTANCE` and static `Converters` calls. On Kotlin 1.8, the baseline and annotated `Database_Impl.java` files matched exactly after normalizing converter dispatch. The candidate changes only annotation placement, not converter function bodies, and the existing null/current-queue tests passed in all four candidate matrix combinations. That evidence does not prove compatibility with all historical Parcel/Media3 versions. The [earlier diagnostic 45356524364](https://github.com/Vicx01/ViMusic/actions/runs/45356524364) baseline encountered HTTP 429; the later diagnostic overcame it.

**Provisional recommendation:** the two converter annotations in isolation are smaller than upgrading Room/compileSdk and preserve the existing converter bodies. Do not apply them to production: historical queue retention fails and the older binary-produced blob gap remains. Kotlin 1.9 whole-app and provider compatibility are not established.

## Infrastructure

The previous [API-29 CI failure](https://github.com/Vicx01/ViMusic/actions/runs/45210724235) was a Maven Central HTTP 429 fetching a test-platform dependency. A GitHub failed-job rerun returned integration HTTP 403. A fresh run via a branch push is the alternative route; no sandbox download retries are used. Track its actual outcome rather than classifying either infrastructure response as a code regression.

## Production remediation (requires CI verification)

Before changes, checkpoint `7b8a38ec8fa258e0d84306bbbb98ecb312e4dc3d` was archived to local recovery file `room-before-production-7b8a38e.tar.gz` (SHA-256 `2d18b688c40d6be69bb61927e21ca81b42eb10941800bc8497bc539b4b03e28b`). The production converter methods now have `@JvmStatic`; their bodies and stored representation are unchanged.

Registered manual migrations override the generated 3→4 and 5→6 migrations. The former renames the original queue table to a persistent side table while schemas 4 and 5 have no queue. The latter recreates the schema-6 queue and copies every id, opaque blob and nullable position without deserializing; the side table is dropped only after a successful insert. Unexpected conflicts fail the transaction rather than ignoring records. The final schema remains version 23, so users already on version 23 are not migrated or modified. Queue records already deleted by older installed versions cannot be reconstructed. Schema 4/5 databases created by older code have no backup and cannot recover data that was previously deleted. The temporary side table is an intentional extra only during the intermediate schema; the migration helper checks the exported schema with extra-table validation disabled only for the 3→4 endpoint, and asserts the side table and contents explicitly.

The player no longer clears the saved queue during restoration. It allows a transactional replacement only after successful restoration, and reports a refusal in the system log otherwise. If a blob cannot be decoded, the app reports the failure to the user and in a sanitized log and leaves all rows intact; it does not recover or play the unsupported item. When persistent queue is enabled after service startup, an empty saved queue may be safely written, while an existing saved queue is retained until the next successful restore. Recovery of unknown formats needs a genuine older-binary fixture and a separately tested decoder. No claim is made that unsupported items can be played.

[Production CI 45361565095](https://github.com/Vicx01/ViMusic/actions/runs/45361565095) passed the database remediation on Kotlin 1.8.22: build and eleven instrumentation tests each on API 26 and 29. [Production CI 45363065736](https://github.com/Vicx01/ViMusic/actions/runs/45363065736) passed the same matrix after the Kotlin 1.9.25 / Compose 1.5.15 upgrade. The follow-up queue-store safeguards and three targeted tests passed in [Android CI 45369760037](https://github.com/Vicx01/ViMusic/actions/runs/45369760037): 14 required tests each on API 26 and 29. Before that follow-up, commit `e83b779b24a73cd0dd3b2a2d53c6822f35622f02` was archived as `room-before-queue-safety-e83b779.tar.gz` (SHA-256 `8092ec90478126405a85c79b1500b86e00bf533da5b75f458257ebe55e0cada2`).
