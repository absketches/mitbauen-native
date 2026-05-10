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
    stop_backend
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
    print_file_if_present "$TMP_DIR/backend.log" "backend.log"
}

stop_backend() {
    if [ -z "${BACKEND_PID:-}" ] || ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        return 0
    fi

    kill "$BACKEND_PID" 2>/dev/null || true

    shutdown_deadline=$(( $(date +%s) + 10 ))
    while kill -0 "$BACKEND_PID" 2>/dev/null; do
        if [ "$(date +%s)" -ge "$shutdown_deadline" ]; then
            kill -9 "$BACKEND_PID" 2>/dev/null || true
            break
        fi
        sleep 1
    done

    wait "$BACKEND_PID" 2>/dev/null || true
}

seed_e2e_invite() {
    token_hash="${E2E_INVITE_HASH:-}"

    if [ -z "$token_hash" ]; then
        return 0
    fi

    if [ -z "${jdbc_database_url:-}" ] || [ -z "${jdbc_database_user:-}" ]; then
        echo "Skipping E2E invite seed because JDBC connection details are missing"
        return 0
    fi

    case "$jdbc_database_url" in
        jdbc:postgresql://*)
            psql_url="${jdbc_database_url#jdbc:}"
            postgres_address="${jdbc_database_url#jdbc:postgresql://}"
            postgres_host_port="${postgres_address%%/*}"
            postgres_database="${postgres_address#*/}"
            postgres_database="${postgres_database%%\?*}"
            postgres_port="${postgres_host_port##*:}"
            if [ "$postgres_port" = "$postgres_host_port" ]; then
                postgres_port=5432
            fi
            ;;
        *)
            echo "Unsupported jdbc_database_url for E2E invite seed: $jdbc_database_url"
            exit 1
            ;;
    esac

    if command -v psql >/dev/null 2>&1; then
        PGPASSWORD="${jdbc_database_password:-}" \
            psql "$psql_url" -U "$jdbc_database_user" -v ON_ERROR_STOP=1 \
            -c "insert into invite_links (token_hash, is_active, use_count) values ('$token_hash', true, 0) on conflict (token_hash) do nothing"
        return 0
    fi

    if ! command -v docker >/dev/null 2>&1; then
        echo "Missing required command: psql"
        exit 1
    fi

    postgres_container_id="$(docker ps --filter "publish=${postgres_port}" --format '{{.ID}}' | head -n 1)"
    if [ -z "$postgres_container_id" ]; then
        echo "Unable to find a running Postgres container for port ${postgres_port}"
        exit 1
    fi

    docker exec \
        -e "PGPASSWORD=${jdbc_database_password:-}" \
        "$postgres_container_id" \
        psql -U "$jdbc_database_user" -d "$postgres_database" -v ON_ERROR_STOP=1 \
        -c "insert into invite_links (token_hash, is_active, use_count) values ('$token_hash', true, 0) on conflict (token_hash) do nothing"
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

app_service_http_port="$APP_PORT" \
app_public_base_url="${app_public_base_url:-$PLAYWRIGHT_BASE_URL}" \
app_email_from="${app_email_from:-Mitbauen <verify@mail.mitbauen.test>}" \
resend_api_key="${resend_api_key:-test-key}" \
"$BACKEND_BINARY" >"$TMP_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

wait_for_http "http://127.0.0.1:${APP_PORT}/api/auth/session" "auth session API"
wait_for_http "$PLAYWRIGHT_BASE_URL/" "frontend shell"
seed_e2e_invite

cd "$ROOT_DIR/frontend"
PLAYWRIGHT_BASE_URL="$PLAYWRIGHT_BASE_URL" npm run test:e2e
