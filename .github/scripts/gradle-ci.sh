#!/usr/bin/env bash
# Make failures available as check annotations even when the full log cannot be downloaded.
set -uo pipefail
log_file="${RUNNER_TEMP:-/tmp}/vimusic-gradle.log"
./gradlew --no-daemon --console=plain "$@" >"$log_file" 2>&1
result=$?
tail -n 120 "$log_file"
if [ "$result" -ne 0 ]; then
    python3 - "$log_file" <<'PY'
import pathlib, sys
text = pathlib.Path(sys.argv[1]).read_text(errors="replace")
lines = text.splitlines()
relevant = [line for line in lines if line.startswith("e: ") or ": error:" in line]
excerpt = "\n".join((relevant[-30:] + lines[-55:]))[-20000:]
print("::error title=Gradle failed::" + excerpt.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
PY
fi
exit "$result"
