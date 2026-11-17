#!/bin/bash
# Niko's own daemon API (shared/proto) as Go: libcore/nikopb, for nikod's NikoService.
#
#   shared/tool/gen_proto_go.sh           regenerate libcore/nikopb
#   shared/tool/gen_proto_go.sh --check   exit 1 if libcore/nikopb is not what the protos give (CI)
#
# Needs protoc (python3 -m grpc_tools.protoc, pip install grpcio-tools==1.85.1, as desktop/tool/gen_proto.sh) and Go:
# the plugins of the versions below are built into a temporary directory. The generated files are committed: a build
# needs neither.
set -euo pipefail
GEN_GO=v1.36.11
GEN_GRPC=v1.6.0
check=no
if [ "${1:-}" = "--check" ]; then check=yes; fi
root=$(cd "$(dirname "$0")/../.." && pwd)
bin=$(mktemp -d)
out=$(mktemp -d)
trap 'rm -rf "$bin" "$out"' EXIT
GOBIN="$bin" GOFLAGS=-mod=mod go install "google.golang.org/protobuf/cmd/protoc-gen-go@$GEN_GO"
GOBIN="$bin" GOFLAGS=-mod=mod go install "google.golang.org/grpc/cmd/protoc-gen-go-grpc@$GEN_GRPC"
python3 -m grpc_tools.protoc -I "$root/shared/proto" \
  --plugin=protoc-gen-go="$bin/protoc-gen-go" --plugin=protoc-gen-go-grpc="$bin/protoc-gen-go-grpc" \
  --go_out="$out" --go_opt=module=libcore --go-grpc_out="$out" --go-grpc_opt=module=libcore \
  niko/v1/niko_service.proto
if [ "$check" = yes ]; then
  diff -r "$out/nikopb" "$root/libcore/nikopb" > /dev/null || {
    echo "libcore/nikopb is not what shared/proto gives: run shared/tool/gen_proto_go.sh" >&2
    diff -r "$out/nikopb" "$root/libcore/nikopb" | head -20 >&2 || true
    exit 1
  }
  exit 0
fi
rm -rf "$root/libcore/nikopb"
cp -r "$out/nikopb" "$root/libcore/nikopb"
