#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

resolve_default_jar() {
  local hard_coded="$PROJECT_ROOT/target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar"

  if [[ -f "$hard_coded" ]]; then
    printf '%s' "$hard_coded"
    return 0;
  fi

  # Fall back to the first fat-jar we can find in the target directory. This
  # keeps the script working even if the artifact version changes.
  local candidate
  candidate=$(find "$PROJECT_ROOT/target" -maxdepth 1 -type f -name '*-jar-with-dependencies.jar' -print -quit 2>/dev/null || true)
  if [[ -n "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi

  printf '%s' "$hard_coded"
}

DEFAULT_JAR="$(resolve_default_jar)"
if [[ -n "${JAR+x}" ]]; then
  JAR_ENV_SET=1
else
  JAR_ENV_SET=""
fi
JAR="${JAR:-$DEFAULT_JAR}"

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
    # The build may have produced a jar with a new version number. Try to
    # refresh the default jar path before giving up.
    if [[ -z "${JAR_ENV_SET:-}" ]]; then
      DEFAULT_JAR="$(resolve_default_jar)"
      JAR="$DEFAULT_JAR"
    fi

    echo "[app] Build did not produce $JAR. Please run 'mvn -q -DskipTests package' manually." >&2
    exit 1
  fi
fi

exec java -jar "$JAR" "$@"
