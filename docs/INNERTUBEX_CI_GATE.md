# Real-artifact CI gate

## Status

Prepared, **not executed**. Neither the actual InnerTubeX v0.11.6 artifact nor
its transitive dependencies have been resolved or compiled in this workspace.
This environment has no Android SDK/Gradle and is network-restricted. The user
asked not to retry downloads and not to push without approval. Upstream Vivi's
successful build is not a build of this project. Integration remains **no-go**
until this CI gate passes and its real dependency graph is reviewed.

## Files introduced or changed in this stage

* `experiments/innertubex-artifact-check/` — separate Gradle build inside the
  repository, not included by root `settings.gradle.kts`. Its only production
  dependency is the real `innertubex-android:v0.11.6`; JUnit is test-only. The
  test compiles and instantiates real library classes, with no stubs or network
  requests. It records runtime/test artifact coordinates and SHA-256 hashes.
* `.github/workflows/innertubex-compat.yml` — real artifact resolution, compile,
  tests and dependency trees/insights must pass before the existing standalone
  Vivi-style playback prototype is built in the second job. Reports are uploaded
  even on failure when available. JDK 21, Gradle 9.3.1 and SDK platform 37 are
  requested; their availability and the host toolchain remain unverified.
* `.github/scripts/innertubex-gradle.py` — executes Gradle and reports failures
  in bounded, URL/key/credential-redacted check annotations; captures sanitized
  successful dependency reports. This does not claim all possible secrets can be
  detected, and runtime artifacts should still be treated as sensitive.
* `recovery/vimusic-artifact-gate-checkpoint.tar.gz` — separate verified
  checkpoint of current non-ignored changed files, excluding recovery archives.
  This archive is private, untracked and must not be included in a commit.

The existing `recovery/vimusic-worktree-snapshot.tar.gz` is preserved, SHA-256
`5a27e6cef6c81dc76ccc625f27e97031128518662211145f819c6edcb4bc2fd9`.
No app source, production build configuration, database, lyrics, or minSdk was
changed in this stage. The user authorized API 26 but it is not yet raised.

## Verified locally

* Python runner syntax and synthetic failure redaction/exit propagation passed.
* The experimental Android manifest parsed as XML; the artifact declaration
  points to v0.11.6 and is not included by the root build.
* `git diff --check` passed for tracked edits; generated project/workflow changes
  are untracked and were separately checked as text. These are static checks,
  not Gradle, Kotlin, Android, dependency or playback tests.

## Execution and branch safety

The workflow is triggered by a push touching the project/workflow and supports
manual dispatch once it is on GitHub's default branch. It has not been pushed;
the existing repository default-branch workflow cannot run these uncommitted
files. Local HEAD is `6e83b8b83da13b0c275702a0d6d5bff78e24c07a`; the current
remote branch was observed at `fc9f10c56d5b34a57a3a4b84c7cf1339e042595c`.
The local checkout has existing modified and untracked files. Do not reset,
force-push, discard the snapshot or attempt an unreviewed merge.

A safe candidate after the user explicitly approves a push: verify that the
remote branch tip and the prior content comparison are unchanged; use a separate
temporary checkout of **the same current branch** at its remote tip, copy only
reviewed compatibility files into it, commit, and fast-forward push that same
branch. This leaves the existing dirty workspace and private backup untouched.
Confirm the exact file list and ancestry immediately before doing so. If the
remote moved or a non-fast-forward push would be necessary, stop and report.
No changes to the default branch or separate branch are needed. An alternative
is running the standalone build on a network-enabled machine without pushing.

## Interpretation of future results

A passing `real-artifact` job establishes that this isolated Android host resolves
and uses the published artifact, not that it works with ViMusic's existing Kotlin,
Ktor, Compose, Room and Media3 versions or plays music. Review compile/runtime and
test graphs, insights, manifest/minSdk, artifact identities, and the prototype
job before proposing any production upgrade. Hashes are identifiers, not trusted
checksum verification. Only then proceed separately through dependency migration,
BotGuard, Media3, recovery, and TTML, preserving existing data and functionality.
Real device playback past 30 seconds and through completion remains required.
