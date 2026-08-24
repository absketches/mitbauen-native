#!/bin/sh

set -eu

APP_DIR="${MITBAUEN_APP_DIR:-/opt/mitbauen}"
ENV_FILE="${MITBAUEN_ENV_FILE:-/etc/mitbauen/mitbauen.env}"
BACKEND_BINARY="${MITBAUEN_BACKEND_BINARY:-$APP_DIR/backend/mitbauen-native-backend}"
BACKEND_JAR="${MITBAUEN_BACKEND_JAR:-$APP_DIR/backend/mitbauen-native-backend.jar}"
COMMAND="backfill-project-description-translations"

load_value() {
    key="$1"
    if [ ! -f "$ENV_FILE" ]; then
        return 0
    fi

    value=$(sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1)
    if [ -n "$value" ]; then
        case "$value" in
            \"*\") value=$(printf '%s' "$value" | sed 's/^"//; s/"$//') ;;
            \'*\') value=$(printf '%s' "$value" | sed "s/^'//; s/'$//") ;;
        esac
        export "$key=$value"
    fi
}

load_value jdbc_database_url
load_value jdbc_database_user
load_value jdbc_database_password
load_value app_translation_openai_api_key
load_value app_translation_openai_model

if [ -x "$BACKEND_BINARY" ]; then
    exec "$BACKEND_BINARY" "$COMMAND"
fi

if [ -f "$BACKEND_JAR" ]; then
    exec java -jar "$BACKEND_JAR" "$COMMAND"
fi

echo "No deployed backend artifact found."
echo "Checked:"
echo "  $BACKEND_BINARY"
echo "  $BACKEND_JAR"
exit 1
