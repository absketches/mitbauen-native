#!/bin/sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
ARTIFACTS_DIR="$ROOT_DIR/.artifacts"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"

require_file() {
    if [ ! -f "$1" ]; then
        echo "Missing required file: $1"
        exit 1
    fi
}

echo "Building Mitbauen Native artifacts..."

require_file "$FRONTEND_DIR/package.json"
require_file "$BACKEND_DIR/pom.xml"

rm -rf "$ARTIFACTS_DIR/frontend" "$ARTIFACTS_DIR/backend"
mkdir -p "$ARTIFACTS_DIR/frontend" "$ARTIFACTS_DIR/backend"

echo "Building frontend..."
cd "$FRONTEND_DIR"

if [ -f "pnpm-lock.yaml" ]; then
    if command -v corepack >/dev/null 2>&1; then
        corepack enable >/dev/null 2>&1 || true
    fi
    if ! command -v pnpm >/dev/null 2>&1; then
        echo "pnpm is required because frontend/pnpm-lock.yaml exists."
        exit 1
    fi
    pnpm install --frozen-lockfile
    pnpm run build
elif [ -f "package-lock.json" ]; then
    npm ci
    npm run build
else
    npm install
    npm run build
fi

if [ ! -d "$FRONTEND_DIR/dist" ]; then
    echo "Frontend build did not produce $FRONTEND_DIR/dist"
    exit 1
fi

cp -R "$FRONTEND_DIR/dist/." "$ARTIFACTS_DIR/frontend/"

echo "Building backend..."
cd "$BACKEND_DIR"

if [ -x "./mvnw" ]; then
    MVN_CMD="./mvnw"
elif command -v mvn >/dev/null 2>&1; then
    MVN_CMD="mvn"
else
    echo "Neither backend/mvnw nor mvn is available."
    exit 1
fi

if [ "${BUILD_NATIVE:-0}" = "1" ]; then
    BACKEND_MODE="native"
    MVN_GOALS="clean package -Pnative"
else
    BACKEND_MODE="jar"
    MVN_GOALS="clean package"
fi

if [ "${SKIP_BACKEND_TESTS:-0}" = "1" ]; then
    $MVN_CMD $MVN_GOALS -DskipTests
else
    if [ "$BACKEND_MODE" = "native" ]; then
        $MVN_CMD clean verify -Pnative
    else
        $MVN_CMD clean verify
    fi
fi

if [ "$BACKEND_MODE" = "native" ]; then
    BACKEND_BINARY="$BACKEND_DIR/target/mitbauen-native-backend"
    if [ ! -f "$BACKEND_BINARY" ]; then
        echo "Backend native build did not produce $BACKEND_BINARY"
        exit 1
    fi
    cp "$BACKEND_BINARY" "$ARTIFACTS_DIR/backend/mitbauen-native-backend"
else
    BACKEND_JAR=$(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "original-*.jar" | sort | head -n 1)

    if [ -z "${BACKEND_JAR:-}" ]; then
        echo "Backend build did not produce a runnable JAR in $BACKEND_DIR/target"
        exit 1
    fi

    cp "$BACKEND_JAR" "$ARTIFACTS_DIR/backend/app.jar"
fi

echo "Artifacts ready:"
echo "  Frontend: $ARTIFACTS_DIR/frontend"
if [ "$BACKEND_MODE" = "native" ]; then
    echo "  Backend:  $ARTIFACTS_DIR/backend/mitbauen-native-backend"
else
    echo "  Backend:  $ARTIFACTS_DIR/backend/app.jar"
fi
