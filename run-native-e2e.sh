#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
TMP_DIR="$ROOT_DIR/.tmp/native-e2e"
BACKEND_BINARY="${BACKEND_BINARY:-$ROOT_DIR/.artifacts/backend/mitbauen-native-backend}"
APP_PORT="${APP_PORT:-8080}"
PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:${APP_PORT}}"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-120}"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1"
        exit 1
    fi
}

cleanup() {
    if [ -n "${BACKEND_PID:-}" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        kill "$BACKEND_PID" 2>/dev/null || true
        wait "$BACKEND_PID" 2>/dev/null || true
    fi
}

print_file_if_present() {
    file_path="$1"
    label="$2"
    if [ -f "$file_path" ]; then
        echo "===== $label ====="
        cat "$file_path"
    fi
}

dump_diagnostics() {
    print_file_if_present "$TMP_DIR/migrate.log" "migrate.log"
    print_file_if_present "$TMP_DIR/backend.log" "backend.log"
}

wait_for_http() {
    url="$1"
    label="$2"
    started_at="$(date +%s)"

    while :; do
        if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
            return 0
        fi

        if [ -n "${BACKEND_PID:-}" ] && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
            echo "Backend exited while waiting for $label at $url"
            dump_diagnostics
            exit 1
        fi

        now="$(date +%s)"
        if [ $((now - started_at)) -ge "$STARTUP_TIMEOUT_SECONDS" ]; then
            echo "Timed out waiting for $label at $url after ${STARTUP_TIMEOUT_SECONDS}s"
            dump_diagnostics
            echo "===== last response from $url ====="
            curl -i --max-time 5 "$url" || true
            exit 1
        fi

        sleep 1
    done
}

trap cleanup EXIT

require_command curl

if [ ! -f "$BACKEND_BINARY" ]; then
    echo "Backend binary not found: $BACKEND_BINARY"
    exit 1
fi

mkdir -p "$TMP_DIR"
rm -rf "$TMP_DIR"/*

"$BACKEND_BINARY" migrate >"$TMP_DIR/migrate.log" 2>&1

app_service_http_port="$APP_PORT" "$BACKEND_BINARY" serve >"$TMP_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

wait_for_http "http://127.0.0.1:${APP_PORT}/api/projects" "project feed API"
wait_for_http "$PLAYWRIGHT_BASE_URL/" "frontend shell"

cd "$ROOT_DIR/frontend"
PLAYWRIGHT_BASE_URL="$PLAYWRIGHT_BASE_URL" npm run test:e2e
