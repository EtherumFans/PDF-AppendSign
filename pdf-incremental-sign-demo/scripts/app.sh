#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [[ ! -f "$JAR" ]]; then
  echo "[app] Missing CLI jar at $JAR. Build with 'mvn -q -DskipTests package'." >&2
  exit 1
fi

exec java -jar "$JAR" "$@"
