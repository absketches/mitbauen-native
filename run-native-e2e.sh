#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
TMP_DIR="$ROOT_DIR/.tmp/native-e2e"
BACKEND_BINARY="${BACKEND_BINARY:-$ROOT_DIR/.artifacts/backend/mitbauen-native-backend}"
FRONTEND_DIST_DIR="${FRONTEND_DIST_DIR:-$ROOT_DIR/.artifacts/frontend}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
NGINX_PORT="${NGINX_PORT:-8088}"
PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:${NGINX_PORT}}"

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
    if [ -f "$TMP_DIR/nginx.pid" ]; then
        nginx -p "$TMP_DIR" -c "$TMP_DIR/nginx.conf" -s stop >/dev/null 2>&1 || true
    fi
}

trap cleanup EXIT

require_command nginx
require_command curl

if [ ! -f "$BACKEND_BINARY" ]; then
    echo "Backend binary not found: $BACKEND_BINARY"
    exit 1
fi

if [ ! -d "$FRONTEND_DIST_DIR" ]; then
    echo "Frontend dist directory not found: $FRONTEND_DIST_DIR"
    exit 1
fi

"$ROOT_DIR/migrate.sh"

mkdir -p "$TMP_DIR"
rm -rf "$TMP_DIR"/*

app_service_http_port="$BACKEND_PORT" "$BACKEND_BINARY" >"$TMP_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

until curl -fsS "http://127.0.0.1:${BACKEND_PORT}/api/projects" >/dev/null 2>&1; do
    sleep 1
done

sed \
    -e "s|{{SERVER_NAME}}|_|g" \
    -e "s|{{WEB_ROOT}}|$FRONTEND_DIST_DIR|g" \
    -e "s|{{BACKEND_PORT}}|$BACKEND_PORT|g" \
    "$ROOT_DIR/infra/nginx/systemd.conf.template" > "$TMP_DIR/mitbauen.conf"

cat > "$TMP_DIR/nginx.conf" <<EOF
pid $TMP_DIR/nginx.pid;
error_log $TMP_DIR/error.log;
events {}
http {
    include /etc/nginx/mime.types;
    access_log $TMP_DIR/access.log;
    include $TMP_DIR/mitbauen.conf;
}
EOF

nginx -p "$TMP_DIR" -c "$TMP_DIR/nginx.conf"

until curl -fsS "$PLAYWRIGHT_BASE_URL/" >/dev/null 2>&1; do
    sleep 1
done

cd "$ROOT_DIR/frontend"
PLAYWRIGHT_BASE_URL="$PLAYWRIGHT_BASE_URL" npm run test:e2e
