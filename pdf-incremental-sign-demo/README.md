# PDF Incremental Nursing Record Demo (iText 5.5.6 Edition)

This module recreates the nursing-record signing workflow using **iText 5.5.6**.
The original project targeted iText 7 and relied on its richer `kernel`,
`layout`, and `sign` modules. The downgrade keeps the core append-mode signing
scenario intact while documenting the features that cannot be reproduced with
the legacy API surface.

## Build

```bash
cd pdf-incremental-sign-demo
mvn -q -DskipTests package
```

The command produces
`target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar`.
(Maven Central blocks the build inside the sandbox with HTTP 403 responses; run
it on a network that can access Maven plugins.)

## CLI overview

All commands are exposed through the shaded JAR:

```bash
java -jar target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar --help
```

### `create-template`

```
create-template --out <file> [--rows N] [--certP12 p12] [--password pwd]
```

Creates a one-page A4 nursing form with N rows. When `--certP12` is provided the
result is DocMDP-certified using the supplied PKCS#12 (password defaults to
`123456`). A temporary file is used to avoid in-place truncation during
certification.

### `sign-row`

```
sign-row --src <in> --dest <out> --row <n> --time <text> --text <text> \
         --nurse <name> [--pkcs12 <p12>] [--password <pwd>] [--tsa <url>]
```

Fills the row fields created by `create-template` and signs the corresponding
`nurseSign_n` field in append mode. A demo certificate is generated on the fly
when no PKCS#12 is supplied.

### `sign-electronic`

```
sign-electronic --src <in> --dest <out> [--pkcs12 <p12>] [--password <pwd>] \
                [--page p] [--x pts] [--y pts] [--width pts] [--height pts] \
                [--field name] [--signer name] [--reason text] \
                [--location text] [--contact text] [--cjk-font font] [--tsa url]
```

Appends a visible signature using a dedicated signature field (created on demand
when missing). Layer 2 text can be rendered with a custom CJK font.

### `verify`

```
verify --pdf <file>
```

Lists every signature, verifies the PKCS#7 container, and reports whether a
DocMDP certification dictionary is present. Deep FieldMDP diagnostics from the
iText 7 branch are not available in iText 5.

### `certify`

```
certify --src <in> --dest <out> [--certP12 p12] [--password pwd]
```

Applies DocMDP certification in append mode. The command copies the source file
before signing to avoid truncating the original revision.

### `gen-demo-p12`

```
gen-demo-p12 --out <file> --password <pwd> --cn <name>
```

Generates a self-signed PKCS#12 bundle for testing.

## iText 7 features without iText 5.5.6 equivalents

| iText 7 feature / class | Previous responsibility | iText 5.5.6 status |
| --- | --- | --- |
| `com.itextpdf.layout` tables and layout engine | Automated template layout, font fallback, and styling (`NursingRecordTemplate`) | Replaced with manual `PdfContentByte` drawing and `TextField` placement. No automatic layout or font fallback. |
| `PdfStructureDump`, `PdfStructureDiff`, `PdfStructureDebugger` | Structure and widget debugging commands | Removed. iText 5 offers no equivalent access to the tagged PDF structure tree without extensive reimplementation. |
| `PostSignSanitizer`, `PdfAcroformNormalizer`, `SignatureWidgetRepairer` | Post-signature clean-up and widget repair helpers | Removed. iText 5 widget/annotation helpers are too limited for the automated repair routines. |
| `SignatureVerifier.deepVerify` reachability checks | FieldMDP coverage reporting for each signature | Not implemented. `AcroFields` in iText 5 exposes only basic locking metadata. |
| Incremental field injection utilities (`FormUtil`, `LayoutUtil`, etc.) | Dynamically adding new fields into arbitrary PDFs | Dropped. iText 5 lacks the high-level field builders used in the injection workflow. |

## Potential regressions due to missing iText 7 bug fixes

The following areas rely on behavior that was improved in iText 7. They remain
functional for the demo PDFs but can exhibit issues on complex real-world
documents because iText 5.5.6 predates the fixes:

| Concern | iText 7 component with the fix | iText 5.5.6 surface used now | Risk |
| --- | --- | --- | --- |
| Incremental update correctness for append-mode signatures | `com.itextpdf.signatures.PdfSigner` and `PdfWriter` incremental writing improvements | `PdfStamper.createSignature(...)` + `MakeSignature.signDetached(...)` | Complex cross-reference structures or large file updates may lead to invalid byte ranges or corrupted incremental saves. |
| DocMDP certification propagation | `PdfSigner` + `PdfSignatureAppearance#setCertificationLevel` fixes | `PdfSignatureAppearance#setCertificationLevel` when certifying via `PdfStamper` | Some viewers may misinterpret permission dictionaries, especially with nested certifications or multiple `Perms` entries. |
| Timestamp authority integration and long-term validation | `com.itextpdf.signatures.TSAClientBouncyCastle` updates and DSS helpers | `TSAClientBouncyCastle` from iText 5.5.6 | Limited ETSI/LTV coverage; timestamp responses with modern algorithms might require manual handling. |
| Field locking edge cases | `SignatureUtil`/`PdfAcroForm` improvements in iText 7 | `AcroFields#setFieldProperty(..., PdfFormField.FF_READ_ONLY, ...)` | Some field states (e.g., additional widget appearances) might remain editable because iText 5 lacks consolidated locking helpers. |

## Feature compatibility summary

* Template creation, row signing, electronic signing, DocMDP certification, and
  PKCS#7 verification are fully supported via the CLI.
* Automated structure diagnostics, widget repair, and deep FieldMDP reachability
  reporting are **not** available in this branch.
* When migrating existing workflows from the iText 7 edition, validate the
  output with Adobe Acrobat/Reader or another trusted validator to ensure
  incremental updates behave as expected.
