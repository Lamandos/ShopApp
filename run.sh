#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVER_PID=""
SERVER_LOG="$(mktemp "${TMPDIR:-/tmp}/shopapp-server.XXXXXX")"

cleanup() {
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Stopping ShopApp server..."
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -f -- "$SERVER_LOG"
}

trap cleanup EXIT
trap 'exit 130' INT TERM

cd "$PROJECT_DIR"

if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb was not found in PATH. Install Android SDK Platform-Tools." >&2
    exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
    echo "Error: curl was not found in PATH." >&2
    exit 1
fi

if [[ ! -x "./gradlew" ]]; then
    echo "Error: executable Gradle wrapper ./gradlew was not found." >&2
    exit 1
fi

EMULATOR_SERIAL="$(
    adb devices |
        awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
)"

if [[ -z "$EMULATOR_SERIAL" ]]; then
    echo "Error: no connected Android emulator was found." >&2
    echo "Start an emulator and verify it appears in: adb devices" >&2
    exit 1
fi

echo "Using emulator: $EMULATOR_SERIAL"
echo "Starting Ktor server..."
./gradlew :server:run >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!

for ((attempt = 1; attempt <= 60; attempt++)); do
    if curl --fail --silent --show-error http://127.0.0.1:8080/health >/dev/null; then
        break
    fi

    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Error: ShopApp server stopped during startup." >&2
        tail -n 40 "$SERVER_LOG" >&2
        exit 1
    fi

    if [[ "$attempt" -eq 60 ]]; then
        echo "Error: GET /health did not become ready in 60 seconds." >&2
        tail -n 40 "$SERVER_LOG" >&2
        exit 1
    fi

    sleep 1
done

echo "Server is healthy. Building Android app..."
./gradlew :app:assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_PATH" ]]; then
    echo "Error: APK was not created at $APK_PATH." >&2
    exit 1
fi

echo "Installing APK..."
adb -s "$EMULATOR_SERIAL" install -r "$APK_PATH"

echo "Launching ShopApp..."
adb -s "$EMULATOR_SERIAL" shell am start -n com.example.shopapp/.MainActivity

echo "ShopApp is running. Press Ctrl+C to stop the server."
wait "$SERVER_PID"
