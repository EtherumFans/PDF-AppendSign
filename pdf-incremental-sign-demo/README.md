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
`--mode` switch controls the workflow:

* `template` – assume the AcroForm already contains the row. The command only fills and signs.
* `inject` – create the row's fields and signature widget if they are missing, then sign.
* `auto` (default) – detect the mode automatically.

`--certify-on-first-inject` promotes the first inject signature to a DocMDP certification when no DocMDP is present yet.
`--page` specifies the page that holds the row layout (defaults to 1).

### `verify`

```
verify --pdf <file>
```

Lists signatures, their revision coverage, integrity result, DocMDP status, and the FieldMDP locks that each signature enforces.

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

## Notes

* All signing operations use `useAppendMode()` to preserve prior revisions.
* Field rectangles are computed by `LayoutUtil`, so the form scales to any row index the page can fit.
* `verify` prints the DocMDP status, each signature's validity at its revision, and the FieldMDP(INCLUDE) targets so operators can
  confirm which fields were locked.
* The visible signature appearance contains the nurse, time, and reason in Chinese per the requirements. For production deployments
  you may plug in a CJK font instead of Helvetica if precise glyph coverage is required.
