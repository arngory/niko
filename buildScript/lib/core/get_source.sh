#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"
pushd ..

####

if [ ! -d "sing-box" ]; then
  git clone --no-checkout "$REPOSITORY_SING_BOX" sing-box
fi
pushd sing-box
git fetch "$REPOSITORY_SING_BOX" "$COMMIT_SING_BOX" || true
git checkout "$COMMIT_SING_BOX"
popd

####

popd
