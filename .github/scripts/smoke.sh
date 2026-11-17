#!/bin/bash
# Smoke test on an emulator (ci.yml, job smoke): the release APK installs and starts, survives configuration
# changes, and leaves no activity behind (a leak keeps the old ones alive after a GC). Argument: the APK folder.
set -u
pkg=io.niko
apk=$(ls "$1"/*-x86_64.apk 2>/dev/null | head -1)
[ -n "$apk" ] || { echo "::error::no signed x86_64 APK in $1"; ls "$1"; exit 1; }
fail=0
# every adb call that waits on the device has a bound: a hung step fails with a reason, not the job's timeout
echo "== root (for the forced GC of the leak check)"
timeout 30 adb root || echo "adb root: $?"
rooted=0
for i in $(seq 30); do
  [ "$(timeout 5 adb shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && { rooted=1; break; }
  sleep 1
done
echo "root: $rooted"
timeout 60 adb wait-for-device || { echo "::error::the emulator did not come back after adb root"; exit 1; }
echo "== install $apk"
timeout 180 adb install -r -g "$apk" || exit 1
adb logcat -c
echo "== start"
timeout 60 adb shell am start -W -n "$pkg/$pkg.ui.MainActivity" || exit 1
sleep 8
pid=$(adb shell pidof "$pkg" | tr -d '\r')
[ -n "$pid" ] || { echo "the app is not running after the start"; fail=1; }

echo "== rotations"
# the activity is recreated on each rotation: the old instances must be collectable
adb shell settings put system accelerometer_rotation 0
for r in 1 0 1 0 1 0; do
  adb shell settings put system user_rotation "$r"
  sleep 2
done
# back to the launcher and in again, twice
for i in 1 2; do
  adb shell input keyevent KEYCODE_HOME; sleep 2
  timeout 60 adb shell am start -W -n "$pkg/$pkg.ui.MainActivity" > /dev/null; sleep 3
done
pid=$(adb shell pidof "$pkg" | tr -d '\r')
if [ -n "$pid" ] && [ "$rooted" = 0 ]; then
  # the google_apis image always has root: a check that cannot run must not pass
  echo "::error::no root on the emulator: the leak check needs a forced GC"; fail=1
elif [ -n "$pid" ]; then
  # SIGUSR1: ART runs a GC (root on the emulator image)
  for i in 1 2 3; do adb shell kill -10 "$pid"; sleep 2; done
  activities=$(timeout 60 adb shell dumpsys meminfo "$pkg" | tr -d '\r' | sed -n 's/.*Activities: *\([0-9]*\).*/\1/p' | head -1)
  echo "activities alive after the GC: ${activities:-?}"
  if [ -z "$activities" ] || [ "$activities" -gt 1 ]; then echo "activities leak (want 1)"; fail=1; fi
else
  echo "the app died during the rotations"; fail=1
fi

echo "== screenshots (light and dark): the main screen, the drawer, the settings"
# for the eye, not a check: smoke-screens/ in the artifact. The night mode is the system's, as most users have it
shots=smoke-screens
mkdir -p "$shots"
here=$(cd "$(dirname "$0")" && pwd)
adb shell settings put system user_rotation 0
# the toolbar's navigation button by its place (it has no description): 28dp from the left, under a 24dp status bar
density=$(timeout 10 adb shell wm density | tr -d '\r' | sed -n 's/.*: *\([0-9]*\)$/\1/p' | tail -1)
density=${density:-160}
menu_x=$((28 * density / 160)); menu_y=$((52 * density / 160))
# tap_text TEXT: the node with this text, by uiautomator's dump
tap_text() {
  timeout 30 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  timeout 30 adb pull /sdcard/ui.xml "$shots/ui.xml" > /dev/null 2>&1
  local xy
  xy=$(python3 -I "$here/ui-node.py" "$shots/ui.xml" "$1")
  rm -f "$shots/ui.xml"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086 # x and y
  timeout 20 adb shell input tap $xy
}
for mode in no yes; do
  timeout 30 adb shell cmd uimode night "$mode" > /dev/null 2>&1
  adb shell am force-stop "$pkg"; sleep 1
  timeout 60 adb shell am start -W -n "$pkg/$pkg.ui.MainActivity" > /dev/null; sleep 4
  timeout 30 adb exec-out screencap -p > "$shots/main-$mode.png" 2> /dev/null
  timeout 20 adb shell input tap "$menu_x" "$menu_y"; sleep 2
  timeout 30 adb exec-out screencap -p > "$shots/drawer-$mode.png" 2> /dev/null
  # the settings, for the rows' icons
  if tap_text "Settings"; then
    sleep 3
    timeout 30 adb exec-out screencap -p > "$shots/settings-$mode.png" 2> /dev/null
  else
    echo "no Settings in the drawer to tap ($mode)"
    adb shell input keyevent KEYCODE_BACK; sleep 1
  fi
done
timeout 30 adb shell cmd uimode night no > /dev/null 2>&1
ls -la "$shots"

if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $pkg" -A 20 | grep -B2 -A20 "$pkg"; then
  echo "the app crashed or did not answer"; fail=1
fi
echo "--- the app's warnings and errors"
adb logcat -d '*:W' | grep -E "$pkg|niko" | tail -n 40
exit $fail
