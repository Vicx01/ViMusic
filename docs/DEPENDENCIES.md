# Dependencies and licensing

Versions below are direct declared versions in [`settings.gradle.kts`](../settings.gradle.kts), [`build.gradle.kts`](../build.gradle.kts) and module build scripts; transitive resolved versions/licenses are not independently verified. Gradle alias `ktor-client-cio` actually maps to `ktor-client-okhttp`, and both network modules instantiate `OkHttp`.

| Dependency | Version | Use | License assessment |
|---|---:|---|---|
| Android Gradle Plugin | 7.3.0 | Android build | Google/Android tooling; verify artifact metadata |
| Kotlin + serialization plugin | 1.7.20 | Kotlin/compiler and JSON model generation | Apache-2.0 family; verify resolved artifacts |
| Gradle | 7.4 | Wrapper build tool | Apache-2.0; wrapper has no distribution checksum configured |
| Compose UI/foundation/material ripple | 1.3.0-rc01 | UI | AndroidX generally Apache-2.0; verify notices |
| Compose compiler | 1.3.2 | Compose Kotlin compiler plugin | AndroidX; verify artifact metadata |
| Activity Compose | 1.7.0-alpha01 | Activity integration | AndroidX; verify |
| Coil Compose | 2.2.2 | Artwork loading/cache | Apache-2.0 family; verify transitive dependencies |
| Compose Shimmer | 1.0.3 | Loading placeholders | Verify artifact/source license before release |
| Room KTX/compiler | 2.5.0-beta01 | SQLite DAO/ORM and kapt | AndroidX; verify |
| Media3 ExoPlayer | 1.0.0-beta03 | Playback/extraction/cache | AndroidX; verify |
| Ktor client/OkHttp/serialization | 2.1.2 | InnerTube/KuGou HTTP | Apache-2.0 family; verify OkHttp and transitive notices separately |
| kotlinx-coroutines-core | 1.6.4 | asynchronous work | Apache-2.0 family; verify |
| Brotli decoder `org.brotli:dec` | 0.1.2 | Brotli network response decoding | Verify exact artifact license/notice |
| AndroidX Palette | 1.0.0 | dynamic colors | AndroidX; verify |
| desugar_jdk_libs | 1.1.5 | older Android Java API support | Verify artifact and transitive license/notice |
| JUnit | 4.13.2 | JVM test dependency in clients | Verify JUnit and Hamcrest artifact licenses |

**NewPipe Extractor is not declared or used in this checkout**: there is no exact version, initialization, primary/fallback role, stream return type or license obligation from a NewPipe dependency to document. The similarly named app-independent upstream project must not be confused with this repository's Piped-compatible HTTP service.

The repository [`LICENSE`](../LICENSE) is GPL version 3 text. GPL obligations for distribution of the covered work can include providing corresponding source and preserving license/copyright notices, subject to the actual license terms and distribution facts. Third-party license compatibility and notice requirements need a complete resolved dependency/SBOM and asset review; this is not legal advice. Root README credits YouTube-Internal-Clients, ionicons and the app-icon author. Fonts, artwork and transitive/native artifacts also require provenance review. Merely declaring JitPack does not establish an artifact is fetched from it. No dependency vulnerability database or lockfile verification was run; outdated versions are a risk, not proof of a specific CVE. See [build](BUILD_AND_TESTING.md) and [security](SECURITY_AND_PRIVACY.md).
