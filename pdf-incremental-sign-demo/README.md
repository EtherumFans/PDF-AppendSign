# PDF Incremental Nursing Record Demo (iText 5 edition)

This branch demonstrates how to maintain a nursing record PDF with **incremental signatures** using iText 5.5.6 and Java 17.
The workflow focuses on three common tasks:

* generating a reusable AcroForm template with nursing rows and signature fields,
* filling a row and applying a digital signature in append mode, and
* placing a generic visible signature anywhere on the page.

Advanced tooling that relied on iText 7 (structure diffing, widget repair, DocMDP auto-injection, etc.) is no longer available
in this edition. See [`docs/itext5-migration-notes.md`](docs/itext5-migration-notes.md) for a full rundown of the gaps.

## Build

```bash
cd pdf-incremental-sign-demo
mvn -q -DskipTests package
```

The shaded CLI is generated at
`target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar`.

Helper scripts are available for convenience:

* **macOS/Linux:** `./scripts/app.sh <command> [options]`
* **Windows (PowerShell/CMD):** `scripts\app.cmd <command> [options]`

They simply wrap `java -jar …`. Example:

```bash
./scripts/app.sh --help
```

## CLI overview

### `create-template`

```
create-template --out <file> [--rows N]
```

Creates a one-page nursing record template with N rows (default 3). Each row contains text fields for the timestamp, note, and
nurse name plus a signature field named `nurseSign_N`.

### `sign-row`

```
sign-row --src <in> --dest <out> --row <n> --time <text> --text <text> \
         --nurse <name> [--pkcs12 <p12>] [--password <pwd>] \
         [--reason txt] [--location txt] [--contact txt] [--tsaUrl url]
```

Populates the row fields (`recordTime_N`, `recordContent_N`, `nurseName_N`) and signs the corresponding signature field in
append mode. If no PKCS#12 is supplied a throwaway demo keystore is generated automatically.

### `sign-electronic`

```
sign-electronic --src <in> --dest <out> [--pkcs12 <p12>] [--password <pwd>] \
                [--page p] [--x pts] [--y pts] [--width pts] [--height pts] \
                [--field name] [--signer name] [--reason text] [--location text] [--contact text]
```

Creates a visible signature at the requested coordinates and signs it in append mode. The command works with or without a
pre-existing signature field (iText creates one when needed).

### `verify`

```
verify --pdf <file>
```

Re-opens the PDF in append-safe mode, lists the signature fields, and verifies each PKCS#7 signature. The command returns a
non-zero exit code when no signature fields are present or verification fails.

### `gen-demo-p12`

```
gen-demo-p12 --out <file> --password <pwd> --cn <CN>
```

Creates a self-signed PKCS#12 file suitable for demos and local testing.

## Usage example

```bash
# 1) Build a template with three rows
./scripts/app.sh create-template --out nursing-template.pdf --rows 3

# 2) Sign the first row with demo credentials
./scripts/app.sh sign-row \
  --src nursing-template.pdf \
  --dest nursing-row1.pdf \
  --row 1 --time "10:00" --text "晨间巡视：生命体征平稳" --nurse "护士张" \
  --reason "日常护理记录" --location "Ward A"

# 3) Inspect the resulting signatures
./scripts/app.sh verify --pdf nursing-row1.pdf
```

All commands operate in append mode so the original revisions remain intact.
