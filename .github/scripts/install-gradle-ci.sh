#!/usr/bin/env bash
set -euo pipefail
archive="$RUNNER_TEMP/gradle-7.4-bin.zip"
curl --fail --location --retry 4 --retry-all-errors --connect-timeout 45 --max-time 600 \
    https://github.com/gradle/gradle-distributions/releases/download/v7.4.0/gradle-7.4-bin.zip -o "$archive"
echo "8cc27038d5dbd815759851ba53e70cf62e481b87494cc97cfd97982ada5ba634  $archive" | sha256sum --check
unzip -q "$archive" -d "$RUNNER_TEMP"
echo "GRADLE_EXECUTABLE=$RUNNER_TEMP/gradle-7.4/bin/gradle" >> "$GITHUB_ENV"
