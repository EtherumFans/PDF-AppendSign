#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar"
PLAIN_PDF="${1:-$PROJECT_ROOT/nursing_plain.pdf}"
OUTPUT_DIR="$PROJECT_ROOT/target/manual-test"
SIGNED_PDF="$OUTPUT_DIR/nursing_plain_row1_signed.pdf"

if [[ ! -f "$JAR" ]]; then
  echo "[basic-test] Missing CLI jar at $JAR. Build with 'mvn -q -DskipTests package'." >&2
  exit 1
fi

if [[ ! -f "$PLAIN_PDF" ]]; then
  echo "[basic-test] Missing nursing_plain.pdf at $PLAIN_PDF" >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

java -jar "$JAR" app sign-row \
  --mode inject \
  --src "$PLAIN_PDF" \
  --dest "$SIGNED_PDF" \
  --row 1 \
  --time "10:00" \
  --text "自动测试 - 护理记录" \
  --nurse "Test Nurse"

VERIFY_OUTPUT="$OUTPUT_DIR/verify.log"
java -jar "$JAR" app verify --pdf "$SIGNED_PDF" | tee "$VERIFY_OUTPUT"

if ! grep -q "Signature: sig_row_1" "$VERIFY_OUTPUT"; then
  echo "[basic-test] Signature sig_row_1 not found in verification output" >&2
  exit 3
fi

echo "[basic-test] Success: sig_row_1 present" >&2
