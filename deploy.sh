#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
ARTIFACTS_DIR="$ROOT_DIR/.artifacts"
BUNDLE_DIR="$ARTIFACTS_DIR/deploy-bundle"
ARCHIVE_NAME="${DEPLOY_ARCHIVE_NAME:-mitbauen-native-deploy.tgz}"
REMOTE_APP_DIR="${DEPLOY_REMOTE_APP_DIR:-}"

"$ROOT_DIR/build.sh"

echo "Preparing deployment bundle..."
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/.artifacts/backend" "$BUNDLE_DIR/.artifacts/frontend" "$BUNDLE_DIR/db/migrations" "$BUNDLE_DIR/infra/nginx"

cp "$ROOT_DIR/Dockerfile" "$BUNDLE_DIR/Dockerfile"
cp "$ROOT_DIR/docker-compose.deploy.yml" "$BUNDLE_DIR/docker-compose.yml"
cp "$ROOT_DIR/.env.example" "$BUNDLE_DIR/.env.example"
cp "$ROOT_DIR/infra/nginx/deploy.conf" "$BUNDLE_DIR/infra/nginx/deploy.conf"

if [ -d "$ROOT_DIR/db/migrations" ]; then
    cp -R "$ROOT_DIR/db/migrations/." "$BUNDLE_DIR/db/migrations/"
fi

cp "$ARTIFACTS_DIR/backend/app.jar" "$BUNDLE_DIR/.artifacts/backend/app.jar"
cp -R "$ARTIFACTS_DIR/frontend/." "$BUNDLE_DIR/.artifacts/frontend/"

tar -czf "$ARTIFACTS_DIR/$ARCHIVE_NAME" -C "$BUNDLE_DIR" .

echo "Deployment bundle ready:"
echo "  $ARTIFACTS_DIR/$ARCHIVE_NAME"

if [ -z "${DEPLOY_REMOTE_HOST:-}" ] || [ -z "${DEPLOY_REMOTE_USER:-}" ] || [ -z "${DEPLOY_REMOTE_DIR:-}" ]; then
    echo "Set DEPLOY_REMOTE_USER, DEPLOY_REMOTE_HOST, and DEPLOY_REMOTE_DIR to upload and restart remotely."
    exit 0
fi

if [ -z "$REMOTE_APP_DIR" ]; then
    REMOTE_APP_DIR="${DEPLOY_REMOTE_DIR%/}/mitbauen-native"
fi

echo "Uploading bundle to ${DEPLOY_REMOTE_USER}@${DEPLOY_REMOTE_HOST}:${DEPLOY_REMOTE_DIR}"
scp "$ARTIFACTS_DIR/$ARCHIVE_NAME" "${DEPLOY_REMOTE_USER}@${DEPLOY_REMOTE_HOST}:${DEPLOY_REMOTE_DIR%/}/$ARCHIVE_NAME"

if [ "${DEPLOY_REMOTE_RESTART:-1}" != "1" ]; then
    echo "Upload complete. Remote restart skipped."
    exit 0
fi

echo "Restarting remote deployment in $REMOTE_APP_DIR"
ssh "${DEPLOY_REMOTE_USER}@${DEPLOY_REMOTE_HOST}" \
    "mkdir -p '$REMOTE_APP_DIR' && tar -xzf '${DEPLOY_REMOTE_DIR%/}/$ARCHIVE_NAME' -C '$REMOTE_APP_DIR' && cd '$REMOTE_APP_DIR' && docker compose up -d --build"

echo "Deployment successful."
