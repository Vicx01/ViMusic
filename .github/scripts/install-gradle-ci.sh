#!/usr/bin/env bash
set -euo pipefail
archive="$RUNNER_TEMP/gradle-9.3.1-bin.zip"
checksum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
# The Gradle 9.3.1 wrapper has hardcoded 10-second download timeouts. Try the
# official distribution hosts with longer timeouts and verify the published hash.
urls=(
  "https://downloads.gradle.org/distributions/gradle-9.3.1-bin.zip"
  "https://services.gradle.org/distributions/gradle-9.3.1-bin.zip"
  "https://github.com/gradle/gradle-distributions/releases/download/v9.3.1/gradle-9.3.1-bin.zip?cachebust=$(date +%s)"
)
installed=false
for i in "${!urls[@]}"; do
    rm -f "$archive"
    if curl --fail --location --retry 2 --retry-all-errors --connect-timeout 30 --max-time 240 "${urls[$i]}" -o "$archive"; then
        if echo "$checksum  $archive" | sha256sum --check; then installed=true; break; fi
        echo "::warning title=Gradle download::Checksum mismatch for source $((i+1))."
    else
        code=$?
        echo "::warning title=Gradle download::Source $((i+1)) failed with curl exit code $code."
    fi
done
if [[ "$installed" != true ]]; then
    echo '::error title=Gradle download::All verified Gradle 9.3.1 download attempts failed.'
    exit 1
fi
unzip -q "$archive" -d "$RUNNER_TEMP"
echo "GRADLE_EXECUTABLE=$RUNNER_TEMP/gradle-9.3.1/bin/gradle" >> "$GITHUB_ENV"
