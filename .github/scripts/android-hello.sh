#!/bin/bash
# android-hello.sh DEX OUT: on the emulator (browser-hellos.yml), the ClientHello of an app's request, OkHttp over the
# system's TLS (.github/tools/androidhello), to the runner's capture (hello.py) behind 10.0.2.2. OUT gets hello.hex and
# SOURCE.
set -u
dex="$1" out="$2"
mkdir -p "$out"
adb push "$dex" /data/local/tmp/hello.dex > /dev/null
python3 -I .github/scripts/hello.py capture 127.0.0.1:18601 "$out/hello.hex" &
cap=$!
sleep 1
timeout 90 adb shell "CLASSPATH=/data/local/tmp/hello.dex app_process /system/bin Hello niko-capture.example 10.0.2.2 18601"
wait "$cap"
[ -s "$out/hello.hex" ] || { echo "::error::no ClientHello from the app's request"; exit 1; }
release="$(adb shell getprop ro.build.version.release | tr -d '\r')"
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "OkHttp 4.12.0 on Android $release (API $sdk), the system's TLS (Conscrypt), the emulator (google_apis, x86_64), GitHub runner ($(date -u +%F), browser-hellos.yml run ${GITHUB_RUN_ID:-local})" > "$out/SOURCE"
