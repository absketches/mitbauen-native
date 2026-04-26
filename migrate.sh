#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
ENV_FILE=""
MIGRATIONS_DIR=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        *)
            MIGRATIONS_DIR="$1"
            shift 1
            ;;
    esac
done

if [ -n "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    . "$ENV_FILE"
    set +a
fi

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1"
        exit 1
    fi
}

sql_escape() {
    printf '%s' "$1" | sed "s/'/''/g"
}

run_psql() {
    PGPASSWORD="$JDBC_PASSWORD" psql "$DB_URL" -U "$JDBC_USER" -v ON_ERROR_STOP=1 "$@"
}

require_command psql

JDBC_URL=${jdbc_database_url:-${JDBC_DATABASE_URL:-}}
JDBC_USER=${jdbc_database_user:-${JDBC_DATABASE_USER:-}}
JDBC_PASSWORD=${jdbc_database_password:-${JDBC_DATABASE_PASSWORD:-}}

if [ -z "${JDBC_URL:-}" ] || [ -z "${JDBC_USER:-}" ]; then
    echo "Missing database configuration. Set jdbc_database_url/jdbc_database_user (or JDBC_DATABASE_URL/JDBC_DATABASE_USER)."
    exit 1
fi

DB_URL=${JDBC_URL#jdbc:}
MIGRATIONS_DIR=${MIGRATIONS_DIR:-$ROOT_DIR/db/migrations}

if [ ! -d "$MIGRATIONS_DIR" ]; then
    echo "Migration directory not found: $MIGRATIONS_DIR"
    exit 1
fi

run_psql <<'SQL'
create table if not exists schema_migrations (
    version text primary key,
    filename text not null,
    applied_at timestamptz not null default now()
);
SQL

find "$MIGRATIONS_DIR" -maxdepth 1 -type f -name 'V*__*.sql' | while IFS= read -r file; do
    filename=$(basename "$file")
    version=${filename#V}
    version=${version%%__*}
    printf '%010d\t%s\n' "$version" "$file"
done | LC_ALL=C sort | cut -f2- | while IFS= read -r file; do
    filename=$(basename "$file")
    version=${filename#V}
    version=${version%%__*}

    applied=$(run_psql -tA -v version="$version" -c "select 1 from schema_migrations where version = :'version';")
    if [ "$applied" = "1" ]; then
        echo "Skipping already applied migration: $filename"
        continue
    fi

    echo "Applying migration: $filename"
    run_psql -f "$file"
    run_psql -v version="$version" -v filename="$filename" -c \
        "insert into schema_migrations (version, filename) values (:'version', :'filename');"
done

echo "Database migrations are up to date."
