# PDF Incremental PAdES Demo

This demo shows how to build a nursing record PDF that is certified with DocMDP and then signed in increments using FieldMDP locks. Each nursing row can be filled and signed independently while keeping previously signed rows read-only.

## Why DocMDP + FieldMDP + append mode?

* **DocMDP certification** locks the overall document to allow only form filling and additional signatures. Any structural change that is not permitted would invalidate the certification signature.
* **Append mode** (`useAppendMode`) ensures each new signature revision is stored incrementally. Earlier signatures stay valid for their historical revision, which is mandatory for multi-timepoint workflows like nursing logs.
* **FieldMDP (INCLUDE)** lets each signature lock only the fields it owns. After signing row _n_, the fields `row{n}.time`, `row{n}.text`, `row{n}.nurse` become read-only while the other rows remain editable for future shifts.

## Build

```bash
cd pdf-incremental-sign-demo
mvn -q -DskipTests package
```

The build produces `target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar` which can be executed with `java -jar`.

## Step-by-step CLI demo

```bash
cd pdf-incremental-sign-demo

# 0) Create a demo signer certificate (optional)
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  gen-demo-p12 --out demo-signer.p12 --password 123456 --cn "Demo Nurse"

# 1) Create the template with DocMDP certification
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  create-template --out nursing_record_template.pdf --cert demo-signer.p12 --password 123456

# 2) 10:00 sign row 1
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row --src nursing_record_template.pdf --dest nursing_record_10.pdf \
  --row 1 --time "10:00" \
  --text "这是第一条护理记录，10:00 护士查房" \
  --nurse "Nurse Zhang" --pkcs12 demo-signer.p12 --password 123456

# 3) 13:00 sign row 2 (append)
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row --src nursing_record_10.pdf --dest nursing_record_13.pdf \
  --row 2 --time "13:00" \
  --text "这是第二条护理记录，13:00 护士查房" \
  --nurse "Nurse Zhang" --pkcs12 demo-signer.p12 --password 123456

# 4) 15:00 sign row 3 (append)
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  sign-row --src nursing_record_13.pdf --dest nursing_record_15.pdf \
  --row 3 --time "15:00" \
  --text "这是第三条护理记录，15:00 护士查房" \
  --nurse "Nurse Zhang" --pkcs12 demo-signer.p12 --password 123456

# 5) Verify final PDF
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar \
  verify --pdf nursing_record_15.pdf
```

## Inspecting the PDF

Open the resulting PDFs in Adobe Acrobat/Reader:

1. Each signature entry displays the signer name, time, and reason in the visible signature widget.
2. Under **Signatures** panel, choose "Validate All" to confirm each revision. You can open each revision to see the state of the form at that time.
3. Form fields for previously signed rows show lock icons and cannot be edited once their corresponding signature is applied, while unsigned rows remain editable.

## Cautions

* Do not "optimize" or "print to PDF" after signing; these operations rewrite the file and destroy the incremental structure, invalidating previous signatures.
* TSA/OCSP hooks are present but optional. Provide `--tsaUrl` to timestamp the signature if a TSA is available.
* A future `--lang sm2` switch is reserved for integrating alternative crypto providers (not implemented yet).
