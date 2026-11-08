# Changelog

## Playback range investigation (current working tree)

* Removed the resolver's artificial 512 KiB DataSpec limit, retained cache and provider architecture, and exposed sanitized error/HTTP diagnostics.
* Added unexecuted local HTTP/range and 37-second Android playback regression tests with a synthetic fixture. The reported real-world failure remains unverified; a temporary JDK was available but Gradle download, Android SDK and device were unavailable.

## Documentation audit

* Added source-based architecture, flow, network, playback, database, lyrics, dependency, security, build/testing, issue and contributor documentation.
* Documented the absence of NewPipe, the conditional third-party Piped-compatible fallback, the 512 KiB first-resolution timing hypothesis and lack of runtime verification. No app behavior or dependencies changed.
* Build/test invocation was attempted but stopped before Gradle because Java was absent; no runtime/provider testing performed.

## Historical context

The checked-in [`fastlane/metadata/android/en-US/changelogs/`](../fastlane/metadata/android/en-US/changelogs) notes, not independently reverified release history, list: v10 offline playlist and playlist import improvements; v11 radio/queue changes and age-restricted playback claim; v12 synchronized lyrics and lockscreen artwork; v16 bookmarks, quick picks and playlist synchronization; v17 opt-in Android Auto; v19 Android 13, audio-device resume and queue loop; v20 minor fixes. Current source and current provider availability take precedence over historical claims. Repository git history in this checkout is shallow and does not support a complete historical reconstruction.

## Outstanding

Reproduce/diagnose reported playback failure, establish JDK/SDK and run builds/tests, add deterministic coverage, review signing, security/privacy, dependencies/licenses and external-service compatibility. See [known issues](KNOWN_ISSUES.md).
