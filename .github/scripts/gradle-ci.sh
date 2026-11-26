#!/usr/bin/env bash
# Make failures available as check annotations even when the full log cannot be downloaded.
set -uo pipefail
log_file="${RUNNER_TEMP:-/tmp}/vimusic-gradle.log"
# Retry only an explicit dependency-resolution rate limit, not compiler or test failures.
for attempt in 1 2 3; do
    "${GRADLE_EXECUTABLE:-./gradlew}" --no-daemon --console=plain "$@" >"$log_file" 2>&1
    result=$?
    if [ "$result" -eq 0 ]; then break; fi
    if [ "$attempt" -eq 3 ] || ! grep -q 'Received status code 429' "$log_file" || \
       ! grep -q 'Could not resolve' "$log_file" || \
       grep -Eq 'There were failing tests|Tests failed|^e: |: error:' "$log_file"; then
        break
    fi
    echo "::warning title=Maven rate limit::Gradle dependency resolution received HTTP 429; retry $attempt of 2."
    sleep $((attempt * 30))
done
tail -n 120 "$log_file"
if [ "$result" -ne 0 ]; then
    python3 - "$log_file" <<'PY'
import pathlib, sys
text = pathlib.Path(sys.argv[1]).read_text(errors="replace")
lines = text.splitlines()
relevant = [line for line in lines if line.startswith("e: ") or ": error:" in line]
excerpt = "\n".join((relevant[-30:] + lines[-55:]))[-16000:]
for report in pathlib.Path("innertube/build/test-results/test").glob("TEST-*.xml"):
    import xml.etree.ElementTree as ET
    for failure in ET.parse(report).getroot().iter("failure"):
        excerpt += "\n" + (failure.text or "")[:4000]
print("::error title=Gradle failed::" + excerpt.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
PY
fi
exit "$result"
