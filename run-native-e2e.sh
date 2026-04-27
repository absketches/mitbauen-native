#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
TMP_DIR="$ROOT_DIR/.tmp/native-e2e"
BACKEND_BINARY="${BACKEND_BINARY:-$ROOT_DIR/.artifacts/backend/mitbauen-native-backend}"
APP_PORT="${APP_PORT:-8080}"
PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:${APP_PORT}}"

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

until curl -fsS "http://127.0.0.1:${APP_PORT}/api/projects" >/dev/null 2>&1; do
    sleep 1
done

until curl -fsS "$PLAYWRIGHT_BASE_URL/" >/dev/null 2>&1; do
    sleep 1
done

cd "$ROOT_DIR/frontend"
PLAYWRIGHT_BASE_URL="$PLAYWRIGHT_BASE_URL" npm run test:e2e
