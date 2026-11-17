#!/bin/bash
# The daemon's API as Dart: the three services of the core's fork (experimental/boxdd and daemon) into lib/gen.
#
#   tool/gen_proto.sh [CORE]          CORE: a checkout of the fork at the commit the app pins
#                                     (buildScript/lib/core/get_source_env.sh); ../../sing-box by default
#   tool/gen_proto.sh --check [CORE]  exit 1 if lib/gen is not what that checkout gives (CI)
#
# Needs protoc (python3 -m grpc_tools.protoc, pip install grpcio-tools) and protoc-gen-dart of the version below
# (dart pub global activate protoc_plugin $PLUGIN) on PATH. The generated files are committed: a build needs neither.
set -euo pipefail
PLUGIN=25.1.0
check=no
if [ "${1:-}" = "--check" ]; then check=yes; shift; fi
here=$(cd "$(dirname "$0")/.." && pwd)
core=$(cd "${1:-$here/../../sing-box}" && pwd)
have=$(dart pub global list 2> /dev/null | sed -n 's/^protoc_plugin //p')
[ "$have" = "$PLUGIN" ] || { echo "protoc_plugin $PLUGIN is needed, found '${have:-none}'" >&2; exit 2; }
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
python3 -m grpc_tools.protoc -I "$core" --dart_out="grpc:$out" \
  daemon/started_service.proto daemon/managed_service.proto experimental/boxdd/desktop_service.proto
# enum and JSON descriptors the app does not use
find "$out" -name '*.pbjson.dart' -delete
dart format "$out" > /dev/null
if [ "$check" = yes ]; then
  diff -r "$out" "$here/lib/gen" > /dev/null || {
    echo "desktop/lib/gen is not what the core's protos give: run desktop/tool/gen_proto.sh" >&2
    diff -r "$out" "$here/lib/gen" | head -20 >&2 || true
    exit 1
  }
  exit 0
fi
rm -rf "$here/lib/gen"
mkdir -p "$here/lib/gen"
cp -r "$out"/. "$here/lib/gen/"
