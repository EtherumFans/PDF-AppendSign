#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEFAULT_JAR="$PROJECT_ROOT/target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [[ ! -f "$JAR" ]]; then
  echo "[app] Missing CLI jar at $JAR. Attempting to build with 'mvn -q -DskipTests package'." >&2
  if ! command -v mvn >/dev/null 2>&1; then
    echo "[app] Maven is not installed or not on PATH; please install Maven and rerun." >&2
    exit 1
  fi

  if ! (cd "$PROJECT_ROOT" && mvn -q -DskipTests package); then
    echo "[app] Maven build failed; see output above. Please resolve the build issue and rerun." >&2
    exit 1
  fi

  if [[ ! -f "$JAR" ]]; then
    echo "[app] Build did not produce $JAR. Please run 'mvn -q -DskipTests package' manually." >&2
    exit 1
  fi
fi

exec java -jar "$JAR" "$@"
