#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Android SDK is not configured}"
if [[ -f "$ANDROID_HOME/platforms/android-37/android.jar" || -f "$ANDROID_HOME/platforms/android-37.0/android.jar" ]]; then
    echo 'Android compile SDK 37 is already installed.'
    exit 0
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
if ! timeout 600 sdkmanager --install 'platforms;android-37.0' > "${RUNNER_TEMP:?}/vimusic-compile-sdk.log" 2>&1; then
    tail -n 40 "$RUNNER_TEMP/vimusic-compile-sdk.log"
    echo '::error::Android compile SDK 37 installation failed or timed out.'
    exit 1
fi
echo 'Installed Android compile SDK 37.'
