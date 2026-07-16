#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mods_dir="run/mods"
iris_config="run/config/iris.properties"
test_case="${SNAPSHOT_RENDERER_TEST_CASE:-renderer}"
report_prefix="${SNAPSHOT_RENDERER_REPORT_PREFIX:-$test_case}"
state_dir="$(mktemp -d "$PWD/run/.snapshot-renderer-matrix.XXXXXX")"
mkdir -p "$state_dir/mods"

iris_jar="$(find "$mods_dir" -maxdepth 1 -type f -name 'iris-*.jar' -print -quit)"
sodium_jar="$(find "$mods_dir" -maxdepth 1 -type f -name 'sodium-*.jar' -print -quit)"
if [[ -z "$iris_jar" || -z "$sodium_jar" ]]; then
    echo "Renderer matrix requires Iris and Sodium jars in run/mods." >&2
    exit 1
fi

iris_name="$(basename "$iris_jar")"
sodium_name="$(basename "$sodium_jar")"
had_iris_config=false
if [[ -f "$iris_config" ]]; then
    cp "$iris_config" "$state_dir/iris.properties"
    had_iris_config=true
fi

cleanup() {
    for jar in "$state_dir/mods/"*.jar; do
        [[ -e "$jar" ]] || continue
        mv "$jar" "$mods_dir/"
    done
    if [[ "$had_iris_config" == true ]]; then
        cp "$state_dir/iris.properties" "$iris_config"
    else
        rm -f "$iris_config"
    fi
    rm -rf "$state_dir"
}
trap cleanup EXIT INT TERM

disable_mod() {
    local name="$1"
    if [[ -f "$mods_dir/$name" ]]; then
        mv "$mods_dir/$name" "$state_dir/mods/"
    fi
}

enable_mod() {
    local name="$1"
    if [[ -f "$state_dir/mods/$name" ]]; then
        mv "$state_dir/mods/$name" "$mods_dir/"
    fi
}

set_shaders() {
    local enabled="$1"
    local temporary="$state_dir/iris.properties.next"
    mkdir -p "$(dirname "$iris_config")"
    if [[ -f "$iris_config" ]]; then
        awk -v enabled="$enabled" '
            BEGIN { replaced = 0 }
            /^enableShaders=/ { print "enableShaders=" enabled; replaced = 1; next }
            { print }
            END { if (!replaced) print "enableShaders=" enabled }
        ' "$iris_config" > "$temporary"
    else
        printf 'enableShaders=%s\n' "$enabled" > "$temporary"
    fi
    mv "$temporary" "$iris_config"
}

run_case() {
    local slug="$1"
    local renderer="$2"
    local shader="$3"
    ./gradlew snapshotSmokeTest \
        -PsnapshotSmokeCase="$test_case" \
        -PsnapshotSmokeWorld="${SNAPSHOT_SMOKE_WORLD:-New World}" \
        -PsnapshotSmokeExpectedRenderer="$renderer" \
        -PsnapshotSmokeExpectedShader="$shader" \
        --console=plain
    cp "run/snapshot-test-results/$test_case.json" "run/snapshot-test-results/$report_prefix-$slug.json"
}

disable_mod "$iris_name"
disable_mod "$sodium_name"
run_case vanilla "Vanilla" false

enable_mod "$sodium_name"
run_case sodium "Sodium" false

enable_mod "$iris_name"
set_shaders false
run_case iris-no-shader "Iris + Sodium" false

set_shaders true
run_case iris-shader "Iris shader + Sodium" true

echo "Snapshot $test_case renderer matrix passed."
