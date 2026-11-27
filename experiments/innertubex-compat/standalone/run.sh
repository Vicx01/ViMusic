#!/usr/bin/env bash
# Requires JAVA_HOME and KOTLINC pointing to a Kotlin 2.4.21 compiler.
# Builds in a temporary directory; no Android or third-party integration is tested.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
project="$(cd "$here/.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21 installation}"
: "${KOTLINC:?Set KOTLINC to the Kotlin 2.4.21 kotlinc executable}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
"$KOTLINC" \
  "$project/adapter/src/main/kotlin/it/vfsfitvnm/vimusic/prototype/StreamSnapshot.kt" \
  "$project/adapter/src/test/kotlin/it/vfsfitvnm/vimusic/prototype/StreamSnapshotTest.kt" \
  "$here/ExtractedStreamStub.kt" "$here/JunitStub.kt" "$here/RunSnapshotTests.kt" \
  -include-runtime -d "$work/tests.jar"
"$JAVA_HOME/bin/java" -jar "$work/tests.jar"
