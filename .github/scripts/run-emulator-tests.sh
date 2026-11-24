#!/usr/bin/env bash
set -euo pipefail

# Current Android command-line tools need Java 17. Gradle/AGP for this project use Java 11.
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
api="${VIMUSIC_EMULATOR_API:-29}"
case "$api" in 26|29) ;; *) echo "Unsupported emulator API: $api" >&2; exit 1 ;; esac
image="system-images;android-${api};google_apis;x86_64"
# A stalled SDK download should fail visibly instead of consuming the whole CI job.
timeout 600 sdkmanager --install "$image" >"${RUNNER_TEMP}/vimusic-sdk.log" 2>&1 || { tail -n 40 "${RUNNER_TEMP}/vimusic-sdk.log"; exit 1; }
echo "Installed emulator system image for API $api."
ls -l /dev/kvm || true
emulator -accel-check || true
export ANDROID_AVD_HOME="${RUNNER_TEMP}/vimusic-avds"
mkdir -p "$ANDROID_AVD_HOME"
printf 'no\n' | avdmanager create avd --force --name vimusic-test --path "$ANDROID_AVD_HOME/vimusic-test.avd" --package "$image"
echo 'AVD directory:'
ls -la "$ANDROID_AVD_HOME"
emulator -list-avds
test -f "$ANDROID_AVD_HOME/vimusic-test.ini"
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
actual_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r\n')
if [ "$actual_api" != "$api" ]; then
    echo "Expected emulator API $api, got $actual_api" >&2
    exit 1
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm path it.vfsfitvnm.vimusic.debug | grep -q '^package:'
echo "Verified build-job debug APK installation on API $api."
# This AVD is newly created for CI. The instrumentation job recompiles with a
# different ephemeral debug signing key, so remove only this CI installation.
adb uninstall it.vfsfitvnm.vimusic.debug
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
export JAVA_HOME="${VIMUSIC_GRADLE_JAVA_HOME:-${JAVA_HOME_11_X64:?Missing Gradle JDK}}"
export PATH="$JAVA_HOME/bin:$PATH"
.github/scripts/gradle-ci.sh :app:connectedDebugAndroidTest
