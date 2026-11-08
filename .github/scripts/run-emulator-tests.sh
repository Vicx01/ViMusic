#!/usr/bin/env bash
set -euo pipefail

# Current Android command-line tools need Java 17. Gradle/AGP for this project use Java 11.
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
sdkmanager --install 'system-images;android-29;google_apis;x86_64' >"${RUNNER_TEMP}/vimusic-sdk.log" 2>&1 || { tail -n 40 "${RUNNER_TEMP}/vimusic-sdk.log"; exit 1; }
echo 'Installed emulator system image.'
ls -l /dev/kvm || true
emulator -accel-check || true
printf 'no\n' | avdmanager create avd --force --name vimusic-test --package 'system-images;android-29;google_apis;x86_64'
emulator -avd vimusic-test -no-window -no-audio -gpu swiftshader_indirect -no-snapshot -no-boot-anim >"${RUNNER_TEMP}/vimusic-emulator.log" 2>&1 &
emulator_pid=$!
cleanup() {
    result=$?
    if [ "$result" -ne 0 ]; then
        echo '--- emulator log ---'
        tail -n 60 "${RUNNER_TEMP}/vimusic-emulator.log" || true
        echo '--- adb devices ---'
        adb devices -l || true
    fi
    adb emu kill >/dev/null 2>&1 || true
    kill "$emulator_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT

timeout 150 adb wait-for-device
timeout 240 bash -c 'until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do sleep 3; done'
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
export JAVA_HOME="$JAVA_HOME_11_X64"
export PATH="$JAVA_HOME/bin:$PATH"
.github/scripts/gradle-ci.sh :app:connectedDebugAndroidTest
