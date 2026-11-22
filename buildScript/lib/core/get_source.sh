#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"
ROOT="$PWD"
pushd ..

####

if [ ! -d "sing-box" ]; then
  git clone --no-checkout "$REPOSITORY_SING_BOX" sing-box
fi
pushd sing-box
git fetch "$REPOSITORY_SING_BOX" "$COMMIT_SING_BOX" || true
git checkout "$COMMIT_SING_BOX"
popd

# Only the main module's replace counts: libcore builds the core with its own go.mod, so the forks the core pins
# (yggdrasil-go, ironwood) must be pinned the same in libcore/go.mod, or the core builds against the old ones
for module in github.com/yggdrasil-network/yggdrasil-go github.com/Arceliar/ironwood; do
  core=$(grep "^replace $module " sing-box/go.mod || true)
  ours=$(grep "^replace $module " "$ROOT/libcore/go.mod" || true)
  if [ "$core" != "$ours" ]; then
    echo "libcore/go.mod pins $module unlike the core:" >&2
    echo "  core:    $core" >&2
    echo "  libcore: $ours" >&2
    exit 1
  fi
done

####

popd
