#!/usr/bin/env bash
set -euo pipefail

# Current Android command-line tools need Java 17. Gradle/AGP for this project use Java 11.
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
sdkmanager --install 'system-images;android-29;google_apis;x86_64'
printf 'no\n' | avdmanager create avd --force --name vimusic-test --package 'system-images;android-29;google_apis;x86_64'
emulator -avd vimusic-test -no-window -no-audio -gpu swiftshader_indirect -no-snapshot -no-boot-anim >"${RUNNER_TEMP}/vimusic-emulator.log" 2>&1 &
emulator_pid=$!
cleanup() {
    adb emu kill >/dev/null 2>&1 || true
    kill "$emulator_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT

timeout 360 adb wait-for-device
timeout 360 bash -c 'until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do sleep 3; done'
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
export JAVA_HOME="$JAVA_HOME_11_X64"
export PATH="$JAVA_HOME/bin:$PATH"
.github/scripts/gradle-ci.sh :app:connectedDebugAndroidTest
