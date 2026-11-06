#!/usr/bin/env bash
# The slipstream client for each ABI into app/executableSo/<abi>/libslipstream.so (an executable the app runs from its
# libraries, BoxInstance.slipstreamPath), built from arngory/slipstream at COMMIT_SLIPSTREAM with the fork's Android
# build (android/CMakeLists.txt) and its OpenSSL script. Needs the NDK (ANDROID_NDK_HOME), cmake and ninja; ABIS
# narrows the ABIs.
set -euo pipefail
source buildScript/lib/slipstream/env.sh
root=$(pwd)
src="$root/../slipstream"
work="$root/../slipstream-build"
if [ ! -d "$src/.git" ]; then
  git init -q "$src"
  git -C "$src" remote add origin "$REPOSITORY_SLIPSTREAM"
fi
git -C "$src" fetch -q --depth 1 origin "$COMMIT_SLIPSTREAM"
git -C "$src" checkout -q --force "$COMMIT_SLIPSTREAM"
git -C "$src" submodule update -q --init --recursive
for abi in ${ABIS:-arm64-v8a armeabi-v7a x86 x86_64}; do
  "$src/.github/scripts/android-openssl.sh" "$ANDROID_NDK_HOME" "$abi" "$work/openssl-$abi"
  cmake -S "$src/android" -B "$work/build-$abi" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-23 -DANDROID_STL=c++_static \
    -DOPENSSL_ROOT_DIR="$work/openssl-$abi" > /dev/null
  cmake --build "$work/build-$abi" --target slipstream-client > /dev/null
  install -D -m 755 "$work/build-$abi/slipstream-client" "$root/app/executableSo/$abi/libslipstream.so"
done
ls -l "$root"/app/executableSo/*/libslipstream.so
sha256sum "$root"/app/executableSo/*/libslipstream.so
