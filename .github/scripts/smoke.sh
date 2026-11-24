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

echo "== screenshots (light and dark): the main screen's two views, connected, the mode's sheet, the settings, the configs' sheet"
# for the eye, not a check: smoke-screens/ in the artifact. The night mode is the system's, as most users have it.
# The main screen (ui/home) shows the chosen profile (a link's) and once connected the time and speeds; its Connection
# tile opens the configs' sheet, the Advanced view's Connection row the settings
shots=smoke-screens
mkdir -p "$shots"
here=$(cd "$(dirname "$0")" && pwd)
adb shell settings put system user_rotation 0
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
# configs NAME: the main screen's Connection tile opens the configs' sheet; its screenshot, then closed
configs() {
  tap_text "Connection" || { echo "no Connection tile on the main screen"; return 1; }
  sleep 3
  timeout 30 adb exec-out screencap -p > "$shots/configs-$1.png" 2> /dev/null
  adb shell input keyevent KEYCODE_BACK; sleep 1
}
# tap_button TEXT: the positive button of the dialog that shows this text
tap_button() {
  local i xy
  for i in 1 2 3 4 5; do
    timeout 30 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    timeout 30 adb pull /sdcard/ui.xml "$shots/ui.xml" > /dev/null 2>&1
    xy=$(python3 -I "$here/ui-button.py" "$shots/ui.xml" "$1")
    rm -f "$shots/ui.xml"
    # shellcheck disable=SC2086 # x and y
    [ -n "$xy" ] && { timeout 20 adb shell input tap $xy; return; }
    sleep 2
  done
  return 1
}
# restart: the app afresh, on its main screen
restart() {
  adb shell am force-stop "$pkg"; sleep 1
  timeout 60 adb shell am start -W -n "$pkg/$pkg.ui.MainActivity" > /dev/null; sleep 4
}
# a profile from a link, never reached (a documentation address): after the question the configs' sheet opens with its
# row, chosen by a tap
link='vless://00000000-0000-4000-8000-000000000001@192.0.2.10:443?encryption=none&security=tls&sni=smoke.example&type=tcp#Smoke'
timeout 60 adb shell "am start -W -n $pkg/$pkg.ui.MainActivity -a android.intent.action.VIEW -d '$link'" > /dev/null
sleep 3
if tap_button "Import profile"; then
  sleep 3
  tap_text "Smoke" || echo "no row of the imported profile to choose"
else
  echo "no question to import the link"
fi
# the VPN's consent, as the user's tap on the system's dialog gives it
timeout 20 adb shell appops set "$pkg" ACTIVATE_VPN allow || echo "appops ACTIVATE_VPN: $?"
for mode in no yes; do
  timeout 30 adb shell cmd uimode night "$mode" > /dev/null 2>&1
  restart
  timeout 30 adb exec-out screencap -p > "$shots/main-$mode.png" 2> /dev/null
  # connected: the connect button's look, the time and the speeds. Stopped by force: they change every second, and
  # uiautomator's dump waits for a screen that holds still
  if tap_text "Connect"; then
    sleep 8
    timeout 30 adb exec-out screencap -p > "$shots/connected-$mode.png" 2> /dev/null
    restart
  else
    echo "no connect button to tap ($mode)"
  fi
  # the other view, kept by the app: back to the simple one after it
  if tap_text "Advanced"; then
    sleep 2
    timeout 30 adb exec-out screencap -p > "$shots/advanced-$mode.png" 2> /dev/null
    # the settings, for the rows' icons: the Advanced view's Connection row
    if tap_text "Connection"; then
      sleep 3
      timeout 30 adb exec-out screencap -p > "$shots/settings-$mode.png" 2> /dev/null
      adb shell input keyevent KEYCODE_BACK; sleep 1
    else
      echo "no Connection row in the Advanced view to tap ($mode)"
    fi
    tap_text "Simple" || echo "no Simple in the view switch ($mode)"
    sleep 2
  else
    echo "no Advanced in the view switch ($mode)"
  fi
  if tap_text "Mode"; then
    sleep 2
    timeout 30 adb exec-out screencap -p > "$shots/mode-$mode.png" 2> /dev/null
    adb shell input keyevent KEYCODE_BACK; sleep 1
  else
    echo "no Mode tile to tap ($mode)"
  fi
  configs "$mode"
done
timeout 30 adb shell cmd uimode night no > /dev/null 2>&1

echo "== screenshots with a larger font (1.3) and right to left: the main screen and the configs' sheet, light"
# the profile's row and the tiles must not cut their text at 1.3
timeout 30 adb shell settings put system font_scale 1.3
restart
timeout 30 adb exec-out screencap -p > "$shots/main-font-1.3.png" 2> /dev/null
configs font-1.3
timeout 30 adb shell settings put system font_scale 1.0
# right to left: the app in Arabic (its own locale, Android 13+), as the developer options' "Force RTL" does not take
# without the system's configuration changing
timeout 30 adb shell cmd locale set-app-locales "$pkg" --locales ar || echo "no app locale: $?"
restart
timeout 30 adb exec-out screencap -p > "$shots/main-rtl.png" 2> /dev/null
# the tile by its English name: the app's Arabic has no other for it
configs rtl
timeout 30 adb shell cmd locale set-app-locales "$pkg" --locales en-US > /dev/null 2>&1
ls -la "$shots"

if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $pkg" -A 20 | grep -B2 -A20 "$pkg"; then
  echo "the app crashed or did not answer"; fail=1
fi
echo "--- the app's warnings and errors"
adb logcat -d '*:W' | grep -E "$pkg|niko" | tail -n 40
exit $fail
