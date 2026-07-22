#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew clean build --console=plain

for test_case in suite clock image2map ui astro2 items access controls calibration performance highres; do
    ./gradlew snapshotSmokeTest \
        -PsnapshotSmokeCase="$test_case" \
        -PsnapshotSmokeWorld="${SNAPSHOT_SMOKE_WORLD:-New World}" \
        --console=plain
done

if [[ "${SNAPSHOT_SKIP_RENDERER_MATRIX:-false}" != "true" ]]; then
    ./scripts/test-renderers.sh
    ./scripts/test-astro-renderers.sh
fi

echo "Snapshot release tests passed."
