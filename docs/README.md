# ViMusic: source audit and documentation

This documents the checkout at `6e83b8b83da13b0c275702a0d6d5bff78e24c07a` (app version 0.5.4), not other forks or newer ViMusic releases. ViMusic is a Kotlin/Compose Android music player using unofficial YouTube Music APIs, Media3 and local Room storage. Features include search and browsing, playlists, radio, playback in the background, cache-based offline listening, lyrics, a sleep timer and optional Android Auto integration. Availability depends on external services; it is not an official YouTube application.

## Start here

* [Architecture and module map](ARCHITECTURE.md) · [Startup, navigation and UI flow](APP_FLOW.md)
* [External connections and request contracts](API_INTEGRATIONS.md) · [Playback and 30-second investigation](PLAYBACK_ENGINE.md) · [InnerTube 400 request audit](INNERTUBE_REQUEST_AUDIT.md)
* [Database and migrations](DATABASE.md) · [Lyrics](LYRICS_SYSTEM.md)
* [Dependencies and licenses](DEPENDENCIES.md) · [Security and privacy](SECURITY_AND_PRIVACY.md)
* [Build and testing](BUILD_AND_TESTING.md) · [Known issues and priorities](KNOWN_ISSUES.md)
* [Development guide](DEVELOPMENT_GUIDE.md) · [Changelog](CHANGELOG.md)

## Build quick start

Install JDK 11 and Android SDK platform 33 and compatible build tools; configure `ANDROID_HOME` or `sdk.dir` in the untracked `local.properties`. From the repository root run `./gradlew assembleDebug` and install the resulting APK from `app/build/outputs/apk/debug/`. Gradle 7.4 is bootstrapped by the wrapper; network access to Google Maven, Maven Central and the Gradle distribution service is required. See [build details](BUILD_AND_TESTING.md) before distributing a release: the checked-in release build uses the debug signing config.

## Evidence and scope

Most findings are based on repository source/configuration; API examples are illustrative unless explicitly identified as observed. CI built debug APKs and ran synthetic emulator tests. Opt-in, sanitized provider probes reproduced the reported HTTP 400 and compared request profiles, but no physical-device playback fix was verified. No user credentials or raw provider responses were collected; no vulnerability scanner was run. Paths are relative to the repository root; links to source use `../`. See [known issues](KNOWN_ISSUES.md) for confidence and severity.
