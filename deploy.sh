#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
ARTIFACTS_DIR="$ROOT_DIR/.artifacts"
BUNDLE_DIR="$ARTIFACTS_DIR/deploy-bundle"
ARCHIVE_NAME="${DEPLOY_ARCHIVE_NAME:-mitbauen-native-host-deploy.tgz}"
BACKEND_MODE="${DEPLOY_BACKEND_MODE:-native}"

REMOTE_APP_DIR="${DEPLOY_REMOTE_APP_DIR:-/opt/mitbauen}"
REMOTE_ENV_FILE="${DEPLOY_REMOTE_ENV_FILE:-/etc/mitbauen/mitbauen.env}"
REMOTE_SYSTEMD_UNIT="${DEPLOY_REMOTE_SYSTEMD_UNIT:-/etc/systemd/system/mitbauen-backend.service}"
REMOTE_RUN_USER="${DEPLOY_REMOTE_RUN_USER:-${DEPLOY_REMOTE_USER:-mitbauen}}"

escape_sed() {
    printf '%s' "$1" | sed -e 's/[&|]/\\&/g'
}

render_template() {
    template_path="$1"
    output_path="$2"
    shift 2

    rendered=$(cat "$template_path")
    while [ "$#" -gt 1 ]; do
        key="$1"
        value="$(escape_sed "$2")"
        rendered=$(printf '%s' "$rendered" | sed "s|{{$key}}|$value|g")
        shift 2
    done
    printf '%s\n' "$rendered" > "$output_path"
}

if [ "${DEPLOY_USE_EXISTING_ARTIFACTS:-0}" = "1" ]; then
    echo "Using existing artifacts from $ARTIFACTS_DIR"
elif [ "$BACKEND_MODE" = "native" ]; then
    BUILD_NATIVE=1 "${ROOT_DIR}/build.sh"
else
    "${ROOT_DIR}/build.sh"
fi

echo "Preparing single-binary deployment bundle..."
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/backend" "$BUNDLE_DIR/infra/systemd"

if [ "$BACKEND_MODE" = "native" ]; then
    if [ ! -f "$ARTIFACTS_DIR/backend/mitbauen-native-backend" ]; then
        echo "Missing native backend artifact. Re-run with BUILD_NATIVE=1 DEPLOY_BACKEND_MODE=native."
        exit 1
    fi
    cp "$ARTIFACTS_DIR/backend/mitbauen-native-backend" "$BUNDLE_DIR/backend/mitbauen-native-backend"
    BACKEND_EXEC_START_PRE="$REMOTE_APP_DIR/backend/mitbauen-native-backend migrate"
    BACKEND_EXEC_START="$REMOTE_APP_DIR/backend/mitbauen-native-backend serve"
else
    if [ ! -f "$ARTIFACTS_DIR/backend/app.jar" ]; then
        echo "Missing jar backend artifact. Run build.sh without BUILD_NATIVE=1 or set DEPLOY_BACKEND_MODE=native."
        exit 1
    fi
    cp "$ARTIFACTS_DIR/backend/app.jar" "$BUNDLE_DIR/backend/mitbauen-native-backend.jar"
    BACKEND_EXEC_START_PRE="/usr/bin/java -jar $REMOTE_APP_DIR/backend/mitbauen-native-backend.jar migrate"
    BACKEND_EXEC_START="/usr/bin/java -jar $REMOTE_APP_DIR/backend/mitbauen-native-backend.jar serve"
fi

cp "$ROOT_DIR/infra/systemd/mitbauen.env.example" "$BUNDLE_DIR/infra/systemd/mitbauen.env.example"

render_template \
    "$ROOT_DIR/infra/systemd/mitbauen-backend.service.template" \
    "$BUNDLE_DIR/infra/systemd/mitbauen-backend.service" \
    APP_DIR "$REMOTE_APP_DIR" \
    ENV_FILE "$REMOTE_ENV_FILE" \
    RUN_USER "$REMOTE_RUN_USER" \
    EXEC_START_PRE "$BACKEND_EXEC_START_PRE" \
    EXEC_START "$BACKEND_EXEC_START"

tar -czf "$ARTIFACTS_DIR/$ARCHIVE_NAME" -C "$BUNDLE_DIR" .

echo "Deployment bundle ready:"
echo "  $ARTIFACTS_DIR/$ARCHIVE_NAME"

if [ -z "${DEPLOY_REMOTE_HOST:-}" ] || [ -z "${DEPLOY_REMOTE_USER:-}" ]; then
    echo "Set DEPLOY_REMOTE_USER and DEPLOY_REMOTE_HOST to upload and install on the Pi."
    exit 0
fi

REMOTE_ARCHIVE_PATH="${DEPLOY_REMOTE_DIR:-/tmp}/${ARCHIVE_NAME}"

echo "Uploading bundle to ${DEPLOY_REMOTE_USER}@${DEPLOY_REMOTE_HOST}:${REMOTE_ARCHIVE_PATH}"
scp "$ARTIFACTS_DIR/$ARCHIVE_NAME" "${DEPLOY_REMOTE_USER}@${DEPLOY_REMOTE_HOST}:${REMOTE_ARCHIVE_PATH}"

echo "Installing deployment on ${DEPLOY_REMOTE_HOST}"
ssh "${DEPLOY_REMOTE_USER}@${DEPLOY_REMOTE_HOST}" \
    "DEPLOY_REMOTE_USE_SUDO='${DEPLOY_REMOTE_USE_SUDO:-1}' REMOTE_ARCHIVE_PATH='$REMOTE_ARCHIVE_PATH' REMOTE_APP_DIR='$REMOTE_APP_DIR' REMOTE_ENV_FILE='$REMOTE_ENV_FILE' REMOTE_SYSTEMD_UNIT='$REMOTE_SYSTEMD_UNIT' sh -s" <<'EOF'
set -eu

if [ "${DEPLOY_REMOTE_USE_SUDO:-1}" = "1" ] && [ "$(id -u)" -ne 0 ]; then
    SUDO=sudo
else
    SUDO=
fi

TMP_DIR=$(mktemp -d)
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

tar -xzf "$REMOTE_ARCHIVE_PATH" -C "$TMP_DIR"

$SUDO mkdir -p \
    "$REMOTE_APP_DIR/backend" \
    "$(dirname "$REMOTE_ENV_FILE")" \
    "$(dirname "$REMOTE_SYSTEMD_UNIT")"

$SUDO rm -f "$REMOTE_APP_DIR/backend/mitbauen-native-backend" "$REMOTE_APP_DIR/backend/mitbauen-native-backend.jar"
$SUDO cp -R "$TMP_DIR/backend/." "$REMOTE_APP_DIR/backend/"
$SUDO chmod +x "$REMOTE_APP_DIR/backend/mitbauen-native-backend" 2>/dev/null || true
$SUDO cp "$TMP_DIR/infra/systemd/mitbauen-backend.service" "$REMOTE_SYSTEMD_UNIT"

if [ ! -f "$REMOTE_ENV_FILE" ]; then
    $SUDO cp "$TMP_DIR/infra/systemd/mitbauen.env.example" "$REMOTE_ENV_FILE"
    echo "Created $REMOTE_ENV_FILE from the example template."
    echo "Edit the database credentials and rerun deploy.sh before starting the service."
    exit 0
fi

$SUDO systemctl daemon-reload
$SUDO systemctl enable "$(basename "$REMOTE_SYSTEMD_UNIT")"
if $SUDO systemctl is-active --quiet "$(basename "$REMOTE_SYSTEMD_UNIT")"; then
    $SUDO systemctl restart "$(basename "$REMOTE_SYSTEMD_UNIT")"
else
    $SUDO systemctl start "$(basename "$REMOTE_SYSTEMD_UNIT")"
fi

echo "Deployment successful."
EOF
