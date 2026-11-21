#!/bin/bash
# android-hello.sh DEX OUT: on the emulator (browser-hellos.yml), the ClientHello of an app's request, OkHttp over the
# system's TLS (.github/tools/androidhello), to the runner's capture (hello.py) at 203.0.113.1, a docker bridge's
# address (browser-hellos.yml), as e2e.sh reaches the stand. OUT gets hello.hex, SOURCE and SHUFFLE.
set -u
dex="$1" out="$2"
mkdir -p "$out"
host=203.0.113.1
adb push "$dex" /data/local/tmp/hello.dex > /dev/null
second="$(mktemp -d)"
# the captures wait 10 minutes for a whole record each, connections cut short included
python3 -I .github/scripts/hello.py capture "$host:18601" "$out/hello.hex" 600 &
cap=$!
python3 -I .github/scripts/hello.py capture "$host:18701" "$second/hello.hex" 600 &
cap2=$!
sleep 1
# the emulator's network may come up after its boot: each request again every 3 s while its capture still listens
while kill -0 "$cap" 2> /dev/null || kill -0 "$cap2" 2> /dev/null; do
  kill -0 "$cap" 2> /dev/null && timeout 30 adb shell "CLASSPATH=/data/local/tmp/hello.dex app_process /system/bin Hello niko-capture.example $host 18601"
  kill -0 "$cap2" 2> /dev/null && timeout 30 adb shell "CLASSPATH=/data/local/tmp/hello.dex app_process /system/bin Hello niko-capture.example $host 18701"
  sleep 3
done
wait "$cap"
wait "$cap2"
[ -s "$out/hello.hex" ] && [ -s "$second/hello.hex" ] || { echo "::error::no two ClientHellos from the app's request"; exit 1; }
python3 -I .github/scripts/hello.py shuffled "$out/hello.hex" "$second/hello.hex" > "$out/SHUFFLE"
release="$(adb shell getprop ro.build.version.release | tr -d '\r')"
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "OkHttp 4.12.0 on Android $release (API $sdk), the system's TLS (Conscrypt), the emulator (google_apis, x86_64), GitHub runner ($(date -u +%F), browser-hellos.yml run ${GITHUB_RUN_ID:-local})" > "$out/SOURCE"
