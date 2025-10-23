# PDF Incremental Nursing Record Demo

This demo shows how to maintain a nursing record PDF with **incremental signatures**. Each row of the nursing log is protected by
FieldMDP locks, while the document itself is certified with DocMDP. The CLI now supports two workflows:

* **Plan A (template-first):** build a reusable AcroForm template with all rows ahead of time, then sign each row in append mode.
* **Plan B (lazy-inject):** start from a static PDF without a form; each signing step injects the current row's fields and
  signature widget into a new revision before signing it.

The implementation uses Java 17, Maven, iText 7, and BouncyCastle.

## Build

```bash
cd pdf-incremental-sign-demo
mvn -q -DskipTests package
```

The shaded CLI is generated at
`target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar`.

## CLI overview

```bash
java -jar target/...jar app --help
```

> **Note:** Older instructions referenced the `com.demo.pdf.IncrementalSigner` main
> class. It still works for backwards compatibility, but the recommended entry
> point is the shaded JAR's manifest (`java -jar ...`).

### `create-template`

```
create-template --out <file> [--rows N] [--certP12 p12] [--password pwd]
```

Builds an A4 one-page nursing form with *N* rows and pre-defined field names
(`row{n}.time`, `row{n}.text`, `row{n}.nurse`, `sig_row_{n}`). The command applies a
DocMDP certification (form-fill & signatures). If `--certP12` is omitted a demo
certificate is generated automatically.

### `sign-row`

```
sign-row --src <in> --dest <out> --row <n> --time <text> --text <text> \
         --nurse <name> --pkcs12 <p12> --password <pwd> [--tsaUrl <url>] \
         [--mode template|inject|auto] [--page p] [--certify-on-first-inject]
```

Signs a single row in append mode. Common parameters supply the row index and the field values. The
`--mode` switch controls the workflow and enforces a real signature field named `sig_row_{n}`:

* `template` – assumes the AcroForm already contains the row. The command fails if any `rowN.*` or `sig_row_N` field is missing or not `/FT /Sig`.
* `inject` – creates the row's fields and signature widget (with printable annotation flags) when absent, then signs.
* `auto` (default) – detect the mode automatically.

`--certify-on-first-inject` promotes the first inject signature to a DocMDP certification when no DocMDP is present yet.
`--page` specifies the page that holds the row layout (defaults to 1).

Every signing step checks that the output PDF grew in size and re-verifies the document to ensure the new `sig_row_n` is visible to Adobe Reader.

### `verify`

```
verify --pdf <file>
```

Prints field-bound signatures with their filter/subfilter, signer identity, and integrity result. Exits with a non-zero status
when no AcroForm-bound signatures exist or any signature fails validation.

### `certify`

```
certify --src <in> --dest <out> [--certP12 p12] [--password pwd]
```

Applies a DocMDP certification to an existing PDF using append mode. This is useful when a hospital needs to certify a static
layout before running Plan B.

## Usage examples

### Plan A (template-first)

```bash
# 0) Generate a signer certificate (optional)
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  gen-demo-p12 --out demo-signer.p12 --password 123456 --cn "Demo Nurse"

# 1) Create template with 3 rows and certify
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  create-template \
  --out nursing_template.pdf \
  --rows 3 \
  --certP12 demo-signer.p12 \
  --password 123456

# 2) 10:00 sign row 1
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row \
  --mode template \
  --src nursing_template.pdf \
  --dest nursing_10.pdf \
  --row 1 --time "10:00" \
  --text "这是第一条护理记录，10:00 护士查房" \
  --nurse "Nurse Zhang" \
  --pkcs12 demo-signer.p12 --password 123456

# 3) 13:00 sign row 2
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row \
  --mode template \
  --src nursing_10.pdf \
  --dest nursing_13.pdf \
  --row 2 --time "13:00" \
  --text "这是第二条护理记录，13:00 护士查房" \
  --nurse "Nurse Zhang" \
  --pkcs12 demo-signer.p12 --password 123456

# 4) 15:00 sign row 3
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row \
  --mode template \
  --src nursing_13.pdf \
  --dest nursing_15.pdf \
  --row 3 --time "15:00" \
  --text "这是第三条护理记录，15:00 护士查房" \
  --nurse "Nurse Zhang" \
  --pkcs12 demo-signer.p12 --password 123456

# 5) Verify
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  verify --pdf nursing_15.pdf
```

### Plan B (lazy-inject on a static/scanned PDF)

```bash
# Start from a plain PDF without an AcroForm, e.g. nursing_plain.pdf

# 1) 10:00 inject row 1 fields + sign
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row \
  --mode inject \
  --src nursing_plain.pdf \
  --dest nursing_10.pdf \
  --row 1 --time "10:00" \
  --text "这是第一条护理记录，10:00 护士查房" \
  --nurse "Nurse Zhang" \
  --pkcs12 demo-signer.p12 --password 123456 \
  --certify-on-first-inject

# 2) 13:00 inject row 2 fields + sign (append)
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row \
  --mode inject \
  --src nursing_10.pdf \
  --dest nursing_13.pdf \
  --row 2 --time "13:00" \
  --text "这是第二条护理记录，13:00 护士查房" \
  --nurse "Nurse Zhang" \
  --pkcs12 demo-signer.p12 --password 123456

# 3) 15:00 inject row 3 fields + sign (append)
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row \
  --mode inject \
  --src nursing_13.pdf \
  --dest nursing_15.pdf \
  --row 3 --time "15:00" \
  --text "这是第三条护理记录，15:00 护士查房" \
  --nurse "Nurse Zhang" \
  --pkcs12 demo-signer.p12 --password 123456

# 4) Verify
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  verify --pdf nursing_15.pdf
```

If the plain PDF must be certified before any signatures, run `certify` once before step 1:

```bash
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  certify --src nursing_plain.pdf --dest nursing_plain_certified.pdf \
  --certP12 demo-signer.p12 --password 123456
```

## Adobe verification checklist

1. **Run the CLI verifier after every `sign-row`.**

   ```bash
   java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
     verify --pdf nursing_15.pdf
   ```

   The report must list `sig_row_n` with `Filter=/Adobe.PPKLite`, `SubFilter=/adbe.pkcs7.detached`, and `Valid=true`. If the
   list is empty or the filter/subfilter do not match, Adobe Reader will hide the signature.

2. **Open the PDF in Adobe Acrobat/Reader and check the Signatures panel.** Each newly signed row should appear with the same
   field name reported by the verifier.

3. **If Acrobat’s panel is empty, run the structural debugger for hints.**

   ```bash
   java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
     debug-structure --pdf nursing_15.pdf
   ```

   Ensure that:

   * The header at byte 0 reads `%PDF-` (no UTF-8 BOM or stray bytes).
   * The field `sig_row_n` exists in the AcroForm `/Fields` array and its `/V` dictionary reports `Filter=/Adobe.PPKLite`.
   * The signature dictionary shows `/ByteRange` with the first value `0` and `/Contents` as an even-length hex string.

   Fix any failing condition before re-signing; otherwise Adobe will refuse to list the signature.

Additional manual checks in Acrobat Reader:

* Open the PDF and inspect the **Signatures** panel – `sig_row_1`, `sig_row_2`, … should appear for every signed row.
* Each signing produces a slightly larger file size because append mode writes a new incremental revision instead of overwriting earlier bytes.
* Earlier signatures remain valid when you inspect previous revisions via Acrobat’s “View Signed Version”.

## Notes

* All signing operations use `useAppendMode()` to preserve prior revisions.
* Field rectangles are computed by `LayoutUtil`, so the form scales to any row index the page can fit.
* `verify` prints each signature's filter/subfilter, signer CN, sign date, and validation result so you can cross-check Adobe's
  Signatures panel.
* The CLI attempts to embed `fonts/NotoSansCJKsc-Regular.otf` for CJK text; if unavailable it falls back to Helvetica.

## Minimal smoke test

After building the CLI and preparing a `nursing_plain.pdf`, run:

```bash
scripts/basic-inject-test.sh [path/to/nursing_plain.pdf]
```

The script injects row 1, signs it, invokes `verify`, and fails if `sig_row_1` is not detected in the output.
