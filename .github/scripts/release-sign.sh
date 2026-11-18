#!/bin/bash
# Signs a build's APKs and their SHA256SUMS outside the build: release.yml, job sign (environment release), and
# ci.yml, job sign-check, with throwaway keys. No Gradle, plugin or dependency of the build runs where the keys are:
# the APK artifact is data here. Uses only build-tools (zipalign, apksigner) and minisign.
#
# Argument: the APK folder (the build's Niko-<version>-<abi>-unsigned.apk, and UPDATE.json where the release has
# one). Environment:
#   KEYSTORE_FILE, KEYSTORE_PASS, ALIAS_NAME, ALIAS_PASS  the APK key (PKCS12 or JKS)
#   EXPECT_CERT_SHA256    the signer certificate's SHA-256 the APKs must carry, hex (the release key's); required
#   MINISIGN_KEY_FILE     minisign secret key without a password (minisign -G -W); empty: SHA256SUMS goes unsigned,
#                         refused when UPDATE_PUB is set
#   UPDATE_PUB            the public key the app checks updates with (niko-update.pub); empty: no check
#   TAG                   the release tag: the trusted comment "niko <tag>"
set -euo pipefail
dir=$1
: "${KEYSTORE_FILE:?}" "${KEYSTORE_PASS:?}" "${ALIAS_NAME:?}" "${ALIAS_PASS:?}" "${EXPECT_CERT_SHA256:?}" "${TAG:?}"
bt=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
alias_name=$(printf '%s' "$ALIAS_NAME" | xargs)
expect=$(printf '%s' "$EXPECT_CERT_SHA256" | tr 'A-F' 'a-f' | tr -d ': ')

shopt -s nullglob
unsigned=("$dir"/*-unsigned.apk)
[ ${#unsigned[@]} -gt 0 ] || { echo "::error::no unsigned APK in $dir"; ls -la "$dir"; exit 1; }
for apk in "${unsigned[@]}"; do
  out="${apk%-unsigned.apk}.apk"
  # native libraries are compressed (useLegacyPackaging): 4-byte alignment for the uncompressed entries
  # (resources.arsc) is all an APK needs here
  "$bt/zipalign" -f -p 4 "$apk" "$RUNNER_TEMP/aligned.apk"
  "$bt/apksigner" sign --ks "$KEYSTORE_FILE" --ks-key-alias "$alias_name" \
    --ks-pass env:KEYSTORE_PASS --key-pass env:ALIAS_PASS --out "$out" "$RUNNER_TEMP/aligned.apk"
  rm -f "$apk" "$RUNNER_TEMP/aligned.apk" "$out.idsig"
  "$bt/zipalign" -c 4 "$out"
  # both streams: where a build-tools version prints the certificates is not fixed
  if ! certs=$("$bt/apksigner" verify --verbose --print-certs "$out" 2>&1); then
    printf '%s\n' "$certs"
    echo "::error::$(basename "$out"): apksigner verify failed"
    exit 1
  fi
  # every certificate apksigner names, whatever the line's prefix ("Signer #1", or "Signer (minSdkVersion=…)" for
  # signatures per platform version): one certificate in all, the release's
  digests=$(printf '%s\n' "$certs" | sed -n 's/.*certificate SHA-256 digest: *\([0-9a-fA-F]*\).*/\1/p' \
    | tr 'A-F' 'a-f' | sort -u)
  if [ "$digests" != "$expect" ]; then
    printf '%s\n' "$certs"
    echo "::error::$(basename "$out") is signed by [$(printf '%s' "$digests" | tr '\n' ' ')], the release key is $expect"
    exit 1
  fi
  echo "$(basename "$out"): signed, certificate $digests"
done

# the APKs, and the release's UPDATE.json where it has one (client-min-v1 §6): the app reads it only as listed here
(
  cd "$dir" && rm -f SHA256SUMS SHA256SUMS.minisig
  files=(*.apk)
  [ -f UPDATE.json ] && files+=(UPDATE.json)
  sha256sum -- "${files[@]}" > SHA256SUMS
)
if [ -z "${MINISIGN_KEY_FILE:-}" ]; then
  if [ -n "${UPDATE_PUB:-}" ]; then
    echo "::error::the app checks updates with $UPDATE_PUB, and there is no minisign key: it would refuse this release"
    exit 1
  fi
  echo "::warning::no minisign key: SHA256SUMS goes out unsigned"
  exit 0
fi
# The key as minisign reads it: a comment line, then the key line. A secret pasted into a one-line field loses the
# line break ("comment key" on one line) or holds the key line alone, and minisign then says only "Error while
# loading the secret key file". Take the key line wherever it is and write the two lines anew, beside the original
key_line=$(tr -d '\r' < "$MINISIGN_KEY_FILE" | tr ' \t' '\n\n' | grep -E '^[A-Za-z0-9+/]{200,}={0,2}$' | tail -1 || true)
if [ -z "$key_line" ]; then
  echo "::error::the minisign key holds no secret key line ($(grep -c . "$MINISIGN_KEY_FILE" || true) non-empty lines): the secret must be the file minisign -G -W wrote, both lines"
  exit 1
fi
key_file="$MINISIGN_KEY_FILE.2"
(umask 077 && printf 'untrusted comment: minisign secret key\n%s\n' "$key_line" > "$key_file")
if ! out=$(minisign -S -s "$key_file" -m "$dir/SHA256SUMS" -t "niko $TAG" < /dev/null 2>&1); then
  rm -f "$key_file"
  printf '%s\n' "$out"
  case "$out" in
    *[Pp]assword*) echo "::error::the minisign key asks for a password: the release key is made without one (minisign -G -W)" ;;
    *) echo "::error::minisign could not sign SHA256SUMS" ;;
  esac
  exit 1
fi
rm -f "$key_file"
if [ -n "${UPDATE_PUB:-}" ]; then
  # as the app checks it (io.niko.utils.Updates): the pinned public key, the trusted comment the tag
  minisign -V -p "$UPDATE_PUB" -m "$dir/SHA256SUMS"
  grep -qxF "trusted comment: niko $TAG" "$dir/SHA256SUMS.minisig"
fi
ls -la "$dir"
