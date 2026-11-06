#!/bin/bash
# The release APK for an emulator (ci.yml, jobs smoke and e2e): without the signing secrets (forks, pull requests from
# them) it is unsigned and does not install, so it gets a key for this emulator only, never the release key.
# Argument: the APK folder.
set -eu
bt=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
# AGP names an unsigned output ...-x86_64-unsigned.apk
apk=$(ls "$1"/*-x86_64.apk "$1"/*-x86_64-unsigned.apk 2>/dev/null | head -1)
[ -n "$apk" ] || { echo "::error::no x86_64 APK among the build's"; ls "$1"; exit 1; }
if ! "$bt/apksigner" verify "$apk" > /dev/null 2>&1; then
  signed="${apk%-unsigned.apk}"; signed="${signed%.apk}.apk"
  keytool -genkeypair -keystore "$RUNNER_TEMP/smoke.jks" -storepass smokekey -keypass smokekey -alias smoke \
    -keyalg RSA -keysize 2048 -validity 1 -dname CN=smoke -noprompt
  "$bt/zipalign" -f -p 4 "$apk" "$RUNNER_TEMP/aligned.apk"
  "$bt/apksigner" sign --ks "$RUNNER_TEMP/smoke.jks" --ks-pass pass:smokekey --out "$signed" "$RUNNER_TEMP/aligned.apk"
  [ "$signed" = "$apk" ] || rm -f "$apk"
  echo "the APK was unsigned: signed with a throwaway key for the emulator ($signed)"
fi
