#!/usr/bin/env bash
# gradle.sh <arguments>: ./gradlew with them, and once more when the first run failed in buildSrc because the
# repositories answered that one of its artifacts is not there: configuring it (its plugins) or compiling it (its
# classpath). Maven Central and the plugin portal answer so now and then for files they serve (kotlin-stdlib 1.9.24
# in the 1.61.0 release, on 8b6b944 and on f5e227a; kotlin-gradle-plugin-api 2.0.21's jar on ae95651; each green on
# its next run). Such a failure comes in seconds, before the app's tasks run; the second run asks the repositories
# again (--refresh-dependencies) after a pause. Any other failure is the build's and is not retried.
set -o pipefail

log=$(mktemp)
./gradlew "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}

if [ "$status" -ne 0 ] && grep -qE "(configuring project|task|configuration) ':buildSrc[:']" "$log" &&
  grep -qE "Could not (find|resolve) " "$log"; then
  echo "::warning::buildSrc's dependencies were not found in any repository: once more in 30 s, asking them again"
  sleep 30
  ./gradlew --refresh-dependencies "$@"
  status=$?
fi

rm -f "$log"
exit "$status"
