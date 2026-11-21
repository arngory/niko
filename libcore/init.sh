#!/bin/bash
set -e

source ../buildScript/lib/core/get_source_env.sh

chmod -R 777 .build 2>/dev/null || true
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# gomobile and gobind at COMMIT_GOMOBILE, in a directory of that commit's own: build.sh puts it first on PATH, where
# gomobile looks for gobind. No `gomobile init`: bind needs none, and it would install gobind@latest over the pin
GOMOBILE_BIN="$GOPATH/gomobile/$COMMIT_GOMOBILE/bin"
if [ ! -x "$GOMOBILE_BIN/gomobile" ] || [ ! -x "$GOMOBILE_BIN/gobind" ]; then
    src="$(mktemp -d)"
    git init -q "$src"
    git -C "$src" remote add origin "$REPOSITORY_GOMOBILE"
    git -C "$src" fetch -q --depth 1 origin "$COMMIT_GOMOBILE"
    git -C "$src" checkout -q FETCH_HEAD
    (cd "$src" && GOBIN="$GOMOBILE_BIN" go install -v ./cmd/gomobile ./cmd/gobind)
    rm -rf "$src"
fi
