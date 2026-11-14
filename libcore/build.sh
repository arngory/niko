#!/bin/bash

source ./env_java.sh || true
source ../buildScript/init/env_ndk.sh

BUILD=".build"

rm -rf $BUILD/android \
  $BUILD/java \
  $BUILD/javac-output \
  $BUILD/src

if [ -z "$GOPATH" ]; then
  GOPATH=$(go env GOPATH)
fi

# The Go the APK's library is built with: the toolchain go.mod names, and no other. gomobile builds in a module of
# its own (go 1.26.0, no toolchain line), where whatever Go is installed would do: the toolchain line alone reached
# the tests and govulncheck, and the library of 1.45.0 was built with go1.26.0 under notes that said 1.26.10.
GOTOOLCHAIN="$(sed -n 's/^toolchain //p' go.mod)"
if [ -z "$GOTOOLCHAIN" ]; then
  echo ">> no toolchain line in libcore/go.mod"
  exit 1
fi
export GOTOOLCHAIN

# Chromium's network stack (naive) comes from the library modules of cronet-go, and a replace in the core's go.mod
# does not reach this module: the ones here are to be the core's, line for line, or the APK carries another
# Chromium than the one the core's own build is checked with
cronet_replaces() { sed -n 's/^replace \(github.com\/sagernet\/cronet-go[^ ]*\) => \(.*\)$/\1 \2/p' "$1" | sort; }
if [ "$(cronet_replaces go.mod)" != "$(cronet_replaces ../../sing-box/go.mod)" ]; then
  echo ">> cronet-go is replaced differently here and in the core:"
  diff <(cronet_replaces go.mod) <(cronet_replaces ../../sing-box/go.mod)
  exit 1
fi
# the Chromium the Android library modules say they are: every ABI's library is to carry that version. The module
# is fetched first: go list gives no directory for one the module cache does not hold yet (a build from nothing)
go mod download github.com/sagernet/cronet-go/lib/android_arm64 || exit 1
CHROMIUM="$(sed -n 's/^const Version = "\(.*\)"$/\1/p' "$(go list -m -f '{{.Dir}}' github.com/sagernet/cronet-go/lib/android_arm64)"/*.go | sort -u)"
if [ -z "$CHROMIUM" ] || [ "$(wc -l <<< "$CHROMIUM")" != 1 ]; then
  echo ">> no single Chromium version in cronet-go's android_arm64 module: '$CHROMIUM'"
  exit 1
fi

export GOBIND="$GOPATH/bin/gobind-matsuri"
"$GOPATH"/bin/gomobile-matsuri bind -v -androidapi 23 -cache "$(realpath $BUILD)" -trimpath -ldflags='-s -w -checklinkname=0' -tags='with_naive_outbound,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_xhttp,with_awg,with_dnstt,with_masterdnsvpn,with_yggdrasil,badlinkname,tfogo_checklinkname0' . || exit 1
rm -r libcore-sources.jar

# what was built says which Go built it: every ABI's library, or no library
rm -rf "$BUILD/built" && mkdir -p "$BUILD/built"
unzip -o -q libcore.aar 'jni/*/libgojni.so' -d "$BUILD/built" || exit 1
for so in "$BUILD"/built/jni/*/libgojni.so; do
  built="$(go version "$so" | awk '{print $NF}')"
  if [ "$built" != "$GOTOOLCHAIN" ]; then
    echo ">> $so was built with $built, not with $GOTOOLCHAIN (libcore/go.mod)"
    exit 1
  fi
  if ! grep -qaF "$CHROMIUM" "$so"; then
    echo ">> $so does not carry Chromium $CHROMIUM (cronet-go's library module)"
    exit 1
  fi
  echo ">> $(basename "$(dirname "$so")"): $built, Chromium $CHROMIUM"
done
rm -rf "$BUILD/built"

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
