#!/bin/bash
# The app end to end on an emulator (ci.yml, job e2e), against the CI node: the invite opened as a link registers the
# device, the subscription brings the node's Hysteria2 profile, the VPN goes up through it, and a request of the
# emulator's reaches the runner's HTTP server from the node's address, where without the VPN, before and after, it
# comes from one of the runner's own (the emulator's network is the runner's sockets). Arguments: the APK folder, the
# invite file, the server's log, the token it serves, the folder for what the emulator showed (a failure's artifact).
set -u
pkg=io.niko
apk=$(ls "$1"/*-x86_64.apk 2>/dev/null | head -1)
[ -n "$apk" ] || { echo "::error::no signed x86_64 APK in $1"; ls "$1"; exit 1; }
invite=$(cat "$2")
weblog=$3
secret=$(cat "$4")
out=$5
here=$(dirname "$0")
mkdir -p "$out"
# raycontrol-interop's addresses: the runner 203.0.113.1 (its server), the node .2
runner=203.0.113.1
node=203.0.113.2
port=8080
# ProxyEntity.TYPE_HYSTERIA
hysteria=15

finish() {
  timeout 30 adb exec-out screencap -p > "$out/screen.png" 2>/dev/null
  timeout 60 adb logcat -d > "$out/logcat.txt" 2>/dev/null
  timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko.log" > /dev/null 2>&1
  { timeout 20 adb shell ip addr; timeout 20 adb shell ip rule; timeout 30 adb shell dumpsys connectivity; } \
    > "$out/network.txt" 2>&1
  status=$1
  if grep -E "FATAL EXCEPTION|ANR in $pkg" -A 20 "$out/logcat.txt" | grep -B2 -A20 "$pkg"; then
    echo "::error::the app crashed or did not answer"; status=1
  fi
  echo "--- the app's warnings and errors"
  grep -E '^[0-9-]+ [0-9:.]+ +[0-9]+ +[0-9]+ [WEF] ' "$out/logcat.txt" | grep -iE "$pkg|niko|libcore|sing-box" | tail -n 60
  if [ "$status" != 0 ] && [ -s "$out/neko.log" ]; then
    echo "--- the core's log (its end)"
    tail -n 80 "$out/neko.log"
  fi
  exit "$status"
}
fail() { echo "::error::$*"; finish 1; }

# fetch TAG: the emulator asks the runner's server for its token, the request marked with TAG. from: the address the
# server saw it from (empty when it did not come); carried: whether the answer carried the token; last: what nc said
fetch() {
  local body
  # the request's side stays open until the answer has come: a Hysteria2 stream closes whole on a half-close (sing-
  # quic's clientConn has no CloseWrite), and nc would half-close at the end of its input
  body=$(timeout 40 adb shell "(printf 'GET /e2e?$1 HTTP/1.0\r\n\r\n'; sleep 3) | nc -w 10 -q 2 $runner $port" 2>&1 | tr -d '\r')
  sleep 1
  from=$(grep -F "\"GET /e2e?$1 " "$weblog" | head -1 | cut -d' ' -f1)
  carried=no
  if grep -qF "$secret" <<< "$body"; then carried=yes; fi
  last=$(head -c 300 <<< "$body")
}

# the ID of the first profile of type $1 in the app's database, empty while there is none
profile() {
  rm -rf "$out/db" && mkdir -p "$out/db"
  for f in sager_net.db sager_net.db-wal sager_net.db-shm; do
    timeout 30 adb pull "/data/data/$pkg/databases/$f" "$out/db/$f" > /dev/null 2>&1
  done
  python3 -I "$here/e2e-profile.py" "$out/db/sager_net.db" "$1"
}

# tap TEXT: taps the positive button of the dialog that shows TEXT, waiting a minute for it
tap() {
  for i in $(seq 30); do
    timeout 30 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    timeout 30 adb pull /sdcard/ui.xml "$out/ui.xml" > /dev/null 2>&1
    xy=$(python3 -I "$here/ui-button.py" "$out/ui.xml" "$1")
    if [ -n "$xy" ]; then
      # shellcheck disable=SC2086 # x and y
      timeout 20 adb shell input tap $xy && return 0
    fi
    sleep 2
  done
  return 1
}

echo "== root (the app's database, its components that are not exported)"
timeout 30 adb root || echo "adb root: $?"
rooted=0
for i in $(seq 30); do
  [ "$(timeout 5 adb shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && { rooted=1; break; }
  sleep 1
done
timeout 60 adb wait-for-device || fail "the emulator did not come back after adb root"
[ "$rooted" = 1 ] || fail "no root on the emulator"

echo "== install $apk"
timeout 180 adb install -r -g "$apk" || fail "the APK did not install"
# the VPN's consent, as the user's tap on the system's dialog gives it
timeout 20 adb shell appops set "$pkg" ACTIVATE_VPN allow || fail "appops ACTIVATE_VPN"
adb logcat -c

echo "== the runner's server, directly"
# the emulator's network comes up a little after the boot
for i in $(seq 20); do
  fetch "direct-$i-$RANDOM"
  [ -n "$from" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ -n "$from" ] && [ "$from" != "$node" ] && [ "$carried" = yes ] ||
  fail "the emulator's own request: from '${from:-nowhere}', token $carried, want the runner's own (nc said: $last)"
echo "directly: the runner's server saw the request from $from"

echo "== the invite, opened as a link"
timeout 60 adb shell "am start -W -n $pkg/$pkg.ui.MainActivity -a android.intent.action.VIEW -d '$invite'" > /dev/null ||
  fail "the invite did not open the app"
tap "raycontrol node" || fail "no question to import the invite on the screen"

echo "== registration and the subscription"
id=
for i in $(seq 45); do
  id=$(profile "$hysteria")
  [ -n "$id" ] && break
  sleep 2
done
[ -n "$id" ] || fail "no Hysteria2 profile from the node after 90 s"
echo "the node's Hysteria2 profile: $id"

echo "== the core's log at debug (a failure's artifact)"
# the app stopped first: its settings are Room's KeyValuePair in configuration.db, the log level a string
timeout 20 adb shell am force-stop "$pkg"
if timeout 20 adb shell "command -v sqlite3" > /dev/null 2>&1; then
  timeout 20 adb shell "sqlite3 /data/data/$pkg/databases/configuration.db \"INSERT OR REPLACE INTO KeyValuePair (key, valueType, value) VALUES ('logLevel', 5, CAST('3' AS BLOB));\"" ||
    echo "the log level not set: the core logs at its default"
else
  echo "no sqlite3 on the emulator: the core logs at its default level"
fi

echo "== the VPN through it"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
  fail "the quick toggle did not start"
for i in $(seq 20); do
  fetch "vpn-$i-$RANDOM"
  [ "$from" = "$node" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ "$from" = "$node" ] && [ "$carried" = yes ] ||
  fail "through the VPN: from '${from:-nowhere}', token $carried, want the node's $node (nc said: $last)"
echo "through the tunnel: the runner's server saw the request from $from"

echo "== the VPN down again"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" > /dev/null || fail "the quick toggle did not stop"
for i in $(seq 10); do
  fetch "after-$i-$RANDOM"
  [ -n "$from" ] && [ "$from" != "$node" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ -n "$from" ] && [ "$from" != "$node" ] && [ "$carried" = yes ] ||
  fail "after the VPN: from '${from:-nowhere}', token $carried, want the runner's own (nc said: $last)"

finish 0
