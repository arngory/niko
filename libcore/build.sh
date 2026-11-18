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
# naive's key pins hold the intermediates that Chromium's store has as trust anchors, and the store is the
# library's: the core's table of them is to be of the same Chromium release, or a new anchor of the library's
# store is missing from the pins and every server under it is refused
ANCHORS="$(sed -n 's/^const ChromiumAnchorsVersion = "\(.*\)"$/\1/p' ../../sing-box/common/certificate/chromium_anchors_neko_generated.go)"
if [ "$ANCHORS" != "$CHROMIUM" ]; then
  echo ">> the core's intermediate anchors are of Chromium '$ANCHORS', the library is Chromium $CHROMIUM:"
  echo ">> in the core: go run ./cmd/internal/update_chromium_anchors $CHROMIUM"
  exit 1
fi

# The Go runtime's defaults on Android: GOOS android is not "linux" to it, so the heap it frees goes back with MADV_FREE,
# which the kernel takes only under pressure; until then the pages count in the process's RSS and PSS (the background
# process after naive's upload: 20 MB of LazyFree in its Go heap, e2e on b248c63). madvdontneed=1 gives them back at
# once, as on Linux. It is no setting go.mod or //go:debug take, so the linker sets the default, the way the go command
# sets it from go.mod: after the go command's own for a module of libcore's go version under this toolchain (none while
# they are of one release), which a later -X would otherwise drop. GODEBUG still overrides it.
probe="$(mktemp -d)"
printf 'module godebugprobe\n\ngo %s\n' "$(sed -n 's/^go //p' go.mod)" > "$probe/go.mod"
printf 'package main\n\nfunc main() {}\n' > "$probe/main.go"
GODEBUG_GO="$(cd "$probe" && go list -f '{{.DefaultGODEBUG}}' .)" || { rm -rf "$probe"; exit 1; }
rm -rf "$probe"
GODEBUG_DEFAULT="${GODEBUG_GO:+$GODEBUG_GO,}madvdontneed=1"
echo ">> the Go runtime's GODEBUG default: $GODEBUG_DEFAULT"

export GOBIND="$GOPATH/bin/gobind-matsuri"
"$GOPATH"/bin/gomobile-matsuri bind -v -androidapi 23 -cache "$(realpath $BUILD)" -trimpath -ldflags="-s -w -checklinkname=0 -X=runtime.godebugDefault=$GODEBUG_DEFAULT" -tags='with_naive_outbound,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_xhttp,with_awg,with_dnstt,with_masterdnsvpn,with_yggdrasil,badlinkname,tfogo_checklinkname0' . || exit 1
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
  if ! grep -qaF "$GODEBUG_DEFAULT" "$so"; then
    echo ">> $so does not carry the GODEBUG default $GODEBUG_DEFAULT"
    exit 1
  fi
  echo ">> $(basename "$(dirname "$so")"): $built, Chromium $CHROMIUM, GODEBUG $GODEBUG_DEFAULT"
done
# the licenses of the Go modules each ABI's library was linked from, by its own build info, go into the AAR as
# assets/licenses/go.json: the app shows them and NOTICE points to them. A module without a license file, or with one
# the script does not know, stops the build here (buildScript/lib/core/licenses.py)
python3 -I ../buildScript/lib/core/licenses.py --aar libcore.aar --out "$BUILD/licenses-go.json" \
  "$BUILD"/built/jni/*/libgojni.so || exit 1
rm -rf "$BUILD/built"

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
