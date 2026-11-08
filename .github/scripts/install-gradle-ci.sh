#!/usr/bin/env bash
set -euo pipefail
archive="$RUNNER_TEMP/gradle-7.4-bin.zip"
checksum=8cc27038d5dbd815759851ba53e70cf62e481b87494cc97cfd97982ada5ba634
# The Gradle 7.4 wrapper has hardcoded 10-second download timeouts. Try the
# official distribution hosts with longer timeouts and verify the published hash.
urls=(
  "https://downloads.gradle.org/distributions/gradle-7.4-bin.zip"
  "https://services.gradle.org/distributions/gradle-7.4-bin.zip"
  "https://github.com/gradle/gradle-distributions/releases/download/v7.4.0/gradle-7.4-bin.zip?cachebust=$(date +%s)"
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
    echo '::error title=Gradle download::All verified Gradle 7.4 download attempts failed.'
    exit 1
fi
unzip -q "$archive" -d "$RUNNER_TEMP"
echo "GRADLE_EXECUTABLE=$RUNNER_TEMP/gradle-7.4/bin/gradle" >> "$GITHUB_ENV"
