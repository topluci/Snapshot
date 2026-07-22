#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew clean test build --console=plain

jar_path="$(find build/libs -maxdepth 1 -type f -name 'snapshot-*.jar' ! -name '*-sources.jar' | sort | tail -n 1)"
if [[ -z "$jar_path" ]]; then
    echo "No release JAR was produced in build/libs." >&2
    exit 1
fi

unzip -t "$jar_path" >/dev/null
(
    cd build/libs
    shasum -a 256 "$(basename "$jar_path")" > SHA256SUMS.txt
)

echo "Release artifact: $jar_path"
echo "Checksum file: build/libs/SHA256SUMS.txt"
