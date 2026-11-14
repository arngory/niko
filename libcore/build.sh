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
# Go 1.26.11 takes no colon in the -l of a package's #cgo LDFLAGS; cronet-go's library modules name their static
# library that way (-L${SRCDIR} -l:libcronet.a). That one flag, whole, and nothing else
export CGO_LDFLAGS_ALLOW='-l:libcronet\.a'

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
  echo ">> $(basename "$(dirname "$so")"): $built"
done
rm -rf "$BUILD/built"

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
