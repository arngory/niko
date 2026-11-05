#!/bin/bash
# Smoke test on an emulator (ci.yml, job smoke): the release APK installs and starts, survives configuration
# changes, and leaves no activity behind (a leak keeps the old ones alive after a GC). Argument: the APK folder.
set -u
pkg=io.niko
apk=$(ls "$1"/*-x86_64.apk | head -1)
fail=0
adb root > /dev/null 2>&1; sleep 2
adb install -r -g "$apk" || exit 1
adb logcat -c
adb shell am start -W -n "$pkg/$pkg.ui.MainActivity" || exit 1
sleep 8
pid=$(adb shell pidof "$pkg" | tr -d '\r')
[ -n "$pid" ] || { echo "the app is not running after the start"; fail=1; }

# the activity is recreated on each rotation: the old instances must be collectable
adb shell settings put system accelerometer_rotation 0
for r in 1 0 1 0 1 0; do
  adb shell settings put system user_rotation "$r"
  sleep 2
done
# back to the launcher and in again, twice
for i in 1 2; do
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell am start -W -n "$pkg/$pkg.ui.MainActivity" > /dev/null; sleep 3
done
pid=$(adb shell pidof "$pkg" | tr -d '\r')
if [ -n "$pid" ]; then
  # SIGUSR1: ART runs a GC (root on the emulator image)
  for i in 1 2 3; do adb shell kill -10 "$pid"; sleep 2; done
  activities=$(adb shell dumpsys meminfo "$pkg" | tr -d '\r' | sed -n 's/.*Activities: *\([0-9]*\).*/\1/p' | head -1)
  echo "activities alive after the GC: ${activities:-?}"
  if [ -z "$activities" ] || [ "$activities" -gt 1 ]; then echo "activities leak (want 1)"; fail=1; fi
else
  echo "the app died during the rotations"; fail=1
fi

if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $pkg" -A 20 | grep -B2 -A20 "$pkg"; then
  echo "the app crashed or did not answer"; fail=1
fi
echo "--- the app's warnings and errors"
adb logcat -d '*:W' | grep -E "$pkg|niko" | tail -n 40
exit $fail
