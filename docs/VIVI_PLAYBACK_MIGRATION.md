# Vivi playback migration — stage 1

## Scope and reference

User approved API 26 as the intended app minimum, a compatibility prototype,
dependency work and a Vivi-style playback adapter. They asked that TTML parser
and provider integration be planned; no production lyrics change is made in
this stage. The Arena session is fixed to branch `arena/aa6bae22-vimusic`; no new
branch was created. Preserve the pre-existing dirty working tree. Do not reset,
remove files, or alter the reference clone.

* Vivi v7.0.1: `ed8fe35442f69d401096c5368cf808778285c0b2`.
* Previous v7.0.0: `2efbd189790782e85086001cb7aabd6572595f49`.
* InnerTubeX v0.11.6: `64c7df4d88d27e6964157b3b341f7ec70f88ea6e`.
* Reference clone: `/home/user/references/vivi-music` (outside the persistent
  workspace, so it may need to be cloned again later). Do not modify the clone.

The local checkout's HEAD is `6e83b8b` while `origin/arena/aa6bae22-vimusic`
was observed at `fc9f10c`; the local tree contains earlier edits and untracked
files. These must not be overwritten or force-pushed. A normal push requires
reconciling ancestry non-destructively; do not assume CI has run on these files.

## Experiment, not a production integration

`experiments/innertubex-compat` is an independent Android library build, not
included in the app. It tries Vivi's Android artifact/toolchain combination and
contains an isolated extraction adapter, stream field mapping, redacted string
representation and an in-memory expiration/session-aware cache. Tests use only
synthetic stream objects. `resolve` is a network operation but is not called by
the tests. A real BotGuard token provider is **not** implemented in this stage;
callers must supply one. The prototype disables HLS, SABR and bounded range
streams, and refuses a chunked result: no multi-request range transport exists.
Its simplistic cache is not connected to Media3 and requires the host to advance
its generation on session changes. It is not a complete replacement for Vivi's
playback stack.

The new workflow `.github/workflows/innertubex-compat.yml` is intended to build
the isolated project after a push touching it and publish offline test results.
It has not run yet. A manually dispatched workflow needs to exist on the default
branch before GitHub permits dispatch. The main app build and minSdk are
unchanged: its app plus all three Compose Android libraries still declare 21.
Changing only the app's minSdk to 26 would not require lowering library minimums,
but all packaging, runtime, CI/emulator and compatibility effects need testing.
Kotlin/Compose, Ktor, AGP, Java, Room/kapt and Media3 version interactions must be
checked; compileSdk, targetSdk, versioning and signing are separate decisions.

### Build status

No Java or Android SDK is installed on the system PATH. The main wrapper initially
failed because Java was unavailable; the old 7.4 wrapper would also be inappropriate
for this isolated AGP 9 build. A temporary JDK 21 and Kotlin 2.4.21 compiler were
installed under `/tmp`. The Vivi 9.3.1 wrapper then failed downloading Gradle
with an SSL handshake error, before the build started; no Android SDK is available.
The adapter build, real-artifact compatibility and Gradle/JUnit tests have not run.

Four stream-snapshot/cache tests **did pass** with the temporary Kotlin compiler
and test-only stand-ins for InnerTubeX's stream type and JUnit assertions. These
exercise the actual prototype snapshot/cache code, not the real library, Android
or a provider. Kotlin/Gradle files were also syntax-parsed, the prototype manifest
and CI YAML were parsed, and root project isolation was checked. Syntax parsing is
not compilation. See the experiment README for reproducible commands and limits.
The prior CI passes in BUILD_AND_TESTING.md refer to the earlier baseline, not this stage.

## Remaining playback stages and safeguards

1. Run isolated build and offline tests; record dependency-resolution output,
   Android artifact/manifest minimum, toolchain incompatibilities and failures.
   If Vivi's pinned versions do not compile with the v0.11.6 artifact, identify
   the smallest tested compatible versions rather than assuming they work.
2. Design a reversible Android adapter boundary to keep existing search and
   metadata in ViMusic's InnerTube module. Review client strategy and signed URL
   headers. Port the PoToken lifecycle and associated HTML only after reviewing
   origin restrictions, WebView callbacks, timeouts, cancellation, dependencies,
   third-party source attribution and privacy. No raw cookies, headers, tokens,
   response bodies or signed URLs in diagnostics.
3. Model URL expiry, session changes, client failures and 403/416 recovery.
   Capture the failed client before invalidating the URL. A 403 is not proof of
   expiry. Avoid broad deletion of cached audio and never retry indefinitely.
4. Design and test a Media3 DataSource which performs successive bounded requests
   when the chosen stream requires them. Do not simply shorten the outer
   DataSpec, as that can signal premature EOF. Protect cached bytes from mixing
   incompatible formats; preserve queue, user data and playback position.
5. Only after reproducible builds and synthetic tests, integrate behind a
   reversible switch, verify existing behavior and carry out live physical-device
   tests with consent. More-than-30-second synthetic playback is not evidence
   that real YouTube songs work. Do not claim a fix until a real song plays
   beyond 30 seconds and through completion on a device.

Rollback: this experiment is isolated; remove it or disable its workflow without
changing app files or user data. Later stages must retain a switch back to the
existing implementation until tested. Do not commit or push over the observed
remote history, and do not remove existing untracked work.

## TTML integration plan (not yet implemented)

ViMusic currently stores fixed and line-synced text in `models/Lyrics.kt` and
renders `KuGou.Lyrics(...).sentences` using `utils/SynchronizedLyrics.kt` and
`ui/screens/player/Lyrics.kt`. There is no existing TTML parser in this checkout.
Vivi's `lyricsProvider/.../betterlyrics/TTMLParser.kt`, `BetterLyrics.kt` and
`TtmlTest.kt` are the references, but Vivi's custom extended LRC lines are not
understood by ViMusic's fixed-position LRC parser.

* Introduce a typed line/word timeline and a namespace-aware, bounded parser;
  preserve word starts/ends, line text, available vocal roles and fallback to
  line-only display. Define supported time syntax rather than claiming complete
  TTML support. Disable DTDs and external entities and bound input size and depth;
  handle malformed documents explicitly. Vivi's parser uses DOM and needs this
  security review before adaptation.
* Introduce a separately selectable TTML provider based on the reviewed Vivi
  provider; make network/lyrics privacy and third-party service availability clear.
  Keep KuGou and InnerTube behavior available; no new provider becomes mandatory.
* Preserve existing Room `fixed`/`synced` rows and edits. Choose and test either a
  backward-compatible typed storage representation or an additive Room migration;
  do not store extended-LRC strings that the existing parser silently misreads.
  Preserve word-level data, and explicitly define how manual edits clear or update
  stale word timings. Test upgrades, rollback, backups and unavailable providers.
* Extend the current player overlay minimally for word highlight when timed words
  exist; otherwise keep its existing synchronized line display. Test line/word
  boundaries, seek, pause, background vocals, namespaces, malformed/hostile XML,
  cancellation, parser errors, and user edits.

## License

Vivi's general code and InnerTubeX are GPLv3. Retain copyright/license notices,
track source commits and attribution, and review all transitive dependencies.
InnerTubeX v0.11.6 declares QuackJS 0.1.4; the upstream tag's license file
states GPLv3, but its resolved artifact and other dependencies still need review.
Vivi's special restriction prohibits copying/adapting its
Musixmatch module without permission: do not copy it. Review provider service
terms and XML/WebView security independently of code licensing.
