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

export GOBIND="$GOPATH/bin/gobind-matsuri"
"$GOPATH"/bin/gomobile-matsuri bind -v -androidapi 23 -cache "$(realpath $BUILD)" -trimpath -ldflags='-s -w -checklinkname=0' -tags='with_naive_outbound,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_xhttp,with_awg,with_dnstt,with_masterdnsvpn,with_yggdrasil,badlinkname,tfogo_checklinkname0' . || exit 1
rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
