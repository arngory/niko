#!/usr/bin/env bash
# gradle.sh <arguments>: ./gradlew with them, and once more when the first run failed configuring buildSrc because
# the repositories answered that its plugins' artifacts are not there. Maven Central and the plugin portal answer so
# now and then for files they serve (kotlin-stdlib 1.9.24 in the 1.61.0 release, on 8b6b944 and on f5e227a, one day,
# each green on its next run). Such a failure comes in seconds, before any task runs; the second run asks the
# repositories again (--refresh-dependencies) after a pause. Any other failure is the build's and is not retried.
set -o pipefail

log=$(mktemp)
./gradlew "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}

if [ "$status" -ne 0 ] && grep -q "A problem occurred configuring project ':buildSrc'" "$log" &&
  grep -qE "Could not (find|resolve) " "$log"; then
  echo "::warning::buildSrc's dependencies were not found in any repository: once more in 30 s, asking them again"
  sleep 30
  ./gradlew --refresh-dependencies "$@"
  status=$?
fi

rm -f "$log"
exit "$status"
