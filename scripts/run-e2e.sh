#!/usr/bin/env bash
#
# run-e2e.sh — Build, start, test, and tear down SimBridge E2E environment.
#
# Usage:
#   ./scripts/run-e2e.sh              # run all tests (unit + integration + e2e)
#   ./scripts/run-e2e.sh --skip-unit  # skip unit tests, only integration/e2e
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONTAINER_NAME="simbridge-e2e"
IMAGE_NAME="simbridge"
PORT=8100
JWT_SECRET="e2e-test-secret-32chars-minimum!!"
MAX_WAIT=30
SKIP_UNIT=false

for arg in "$@"; do
  case "$arg" in
    --skip-unit) SKIP_UNIT=true ;;
  esac
done

cleanup() {
  echo "--- Cleaning up ---"
  docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
}

trap cleanup EXIT

cd "$PROJECT_DIR"

# ---- Build ----
echo "=== Building Docker image ==="
docker build -t "$IMAGE_NAME" -f docker/Dockerfile .

# ---- Start container ----
echo "=== Starting SimBridge container ==="
docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
docker run -d --name "$CONTAINER_NAME" \
  -p "$PORT:$PORT" \
  -e JWT_SECRET="$JWT_SECRET" \
  "$IMAGE_NAME"

# ---- Wait for ready ----
echo "=== Waiting for SimBridge to be ready (max ${MAX_WAIT}s) ==="
for i in $(seq 1 "$MAX_WAIT"); do
  if curl -sf "http://localhost:$PORT/docs" > /dev/null 2>&1; then
    echo "SimBridge is ready after ${i}s."
    break
  fi
  if [ "$i" -eq "$MAX_WAIT" ]; then
    echo "ERROR: SimBridge did not start within ${MAX_WAIT}s."
    echo "--- Container logs ---"
    docker logs "$CONTAINER_NAME"
    exit 1
  fi
  sleep 1
done

# ---- Run tests ----
TEST_EXIT=0

if [ "$SKIP_UNIT" = false ]; then
  echo "=== Running unit tests ==="
  pytest test_auth.py test_endpoints.py test_websocket.py \
    -v --junitxml=results-unit.xml || TEST_EXIT=$?
fi

echo "=== Running integration & E2E tests ==="
pytest test_container.py test_e2e.py test_e2e_advanced.py \
  -v --junitxml=results-integration.xml || TEST_EXIT=$?

# ---- Report ----
if [ "$TEST_EXIT" -eq 0 ]; then
  echo "=== All tests passed ==="
else
  echo "=== Some tests failed (exit code: $TEST_EXIT) ==="
  echo "--- Container logs ---"
  docker logs "$CONTAINER_NAME"
fi

exit "$TEST_EXIT"
