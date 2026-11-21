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
python3 -I .github/scripts/hello.py capture "$host:18601" "$out/hello.hex" &
cap=$!
python3 -I .github/scripts/hello.py capture "$host:18701" "$second/hello.hex" &
cap2=$!
sleep 1
timeout 90 adb shell "CLASSPATH=/data/local/tmp/hello.dex app_process /system/bin Hello niko-capture.example $host 18601"
timeout 90 adb shell "CLASSPATH=/data/local/tmp/hello.dex app_process /system/bin Hello niko-capture.example $host 18701"
wait "$cap"
wait "$cap2"
[ -s "$out/hello.hex" ] && [ -s "$second/hello.hex" ] || { echo "::error::no two ClientHellos from the app's request"; exit 1; }
python3 -I .github/scripts/hello.py shuffled "$out/hello.hex" "$second/hello.hex" > "$out/SHUFFLE"
release="$(adb shell getprop ro.build.version.release | tr -d '\r')"
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "OkHttp 4.12.0 on Android $release (API $sdk), the system's TLS (Conscrypt), the emulator (google_apis, x86_64), GitHub runner ($(date -u +%F), browser-hellos.yml run ${GITHUB_RUN_ID:-local})" > "$out/SOURCE"
