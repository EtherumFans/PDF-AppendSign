#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEFAULT_JAR="$PROJECT_ROOT/target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar"

find_cli_jar() {
  local candidates=()

  # Prefer the shaded "jar-with-dependencies" artifact when available.
  shopt -s nullglob
  candidates=("$PROJECT_ROOT"/target/pdf-incremental-sign-demo-*-jar-with-dependencies.jar)
  shopt -u nullglob
  if (( ${#candidates[@]} > 0 )); then
    printf '%s\n' "${candidates[0]}"
    return 0
  fi

  # Fallback to any other jar so the CLI can still run if the artifact name changes.
  shopt -s nullglob
  candidates=("$PROJECT_ROOT"/target/pdf-incremental-sign-demo-*.jar)
  shopt -u nullglob
  if (( ${#candidates[@]} > 0 )); then
    printf '%s\n' "${candidates[0]}"
    return 0
  fi

  return 1
}

if ! JAR="$(find_cli_jar)"; then
  echo "[app] Missing CLI jar (looked for $DEFAULT_JAR). Attempting to build with 'mvn -q -DskipTests package'." >&2
  if ! command -v mvn >/dev/null 2>&1; then
    echo "[app] Maven is not installed or not on PATH; please install Maven and rerun." >&2
    exit 1
  fi

  if ! (cd "$PROJECT_ROOT" && mvn -q -DskipTests package); then
    echo "[app] Maven build failed; see output above. Please resolve the build issue and rerun." >&2
    exit 1
  fi

  if ! JAR="$(find_cli_jar)"; then
    echo "[app] Build did not produce a CLI jar in $PROJECT_ROOT/target. Please run 'mvn -q -DskipTests package' manually." >&2
    exit 1
  fi
fi

exec java -jar "$JAR" "$@"
