#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

SNAPSHOT_RENDERER_TEST_CASE=astro2 \
SNAPSHOT_RENDERER_REPORT_PREFIX=astro2 \
    ./scripts/test-renderers.sh
