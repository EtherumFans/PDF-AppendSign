# iText 5 migration notes

This project previously depended on iText 7 (kernel/forms/layout/sign) and exposed a large toolbox for inspecting Acrobat
visibility issues, repairing widgets, and diffing AcroForm structures. Migrating to iText 5.5.6 required a substantial rewrite.
This document summarises what changed and highlights the gaps.

## What was rebuilt with iText 5.5.6

* **Template generation** now relies on low-level drawing (`PdfContentByte`) and `TextField`/`PdfFormField` APIs to create the
  nursing table and signature placeholders. See `com.demo.pdf.NursingRecordTemplate`. 【F:pdf-incremental-sign-demo/src/main/java/com/demo/pdf/NursingRecordTemplate.java†L32-L218】
* **Row signing** is implemented with `PdfStamper.createSignature` plus `PrivateKeySignature` and works entirely in append mode.
  See `com.demo.pdf.NursingRecordSigner`. 【F:pdf-incremental-sign-demo/src/main/java/com/demo/pdf/NursingRecordSigner.java†L19-L200】
* **Visible electronic signatures** are handled via `PdfSignatureAppearance.setVisibleSignature(Rectangle, page, fieldName)` and
  share the same signing stack. See `com.demo.pdf.ElectronicSignatureSigner`. 【F:pdf-incremental-sign-demo/src/main/java/com/demo/pdf/ElectronicSignatureSigner.java†L1-L168】
* **Basic verification** uses `AcroFields.verifySignature` and reports validity for each signature. See
  `com.demo.pdf.SignatureVerifier`. 【F:pdf-incremental-sign-demo/src/main/java/com/demo/pdf/SignatureVerifier.java†L1-L36】

## Removed or downgraded features

The following capabilities depended on iText 7-only constructs and do not have equivalents in 5.5.6:

* **DocMDP / FieldMDP helpers.** iText 7's `PdfSigner` offered high-level DocMDP and FieldMDP configuration plus automatic
  locking. iText 5 only exposes low-level `PdfSignatureAppearance.setCertificationLevel`, leaving no straightforward API for the
  previous auto-injection workflow. Commands such as `certify`, `sanitize-acroform`, and FieldMDP debug tooling have been removed.
* **Structure diffing & diagnostics.** Classes such as `PdfStructureDump`, `PdfStructureDiff`, `PdfStructureDebugger`,
  `SignatureDiagnostics`, and `PostSignValidator` relied on iText 7's `PdfDocument` object model (with `PdfObject` subclasses that
  expose revision reachability). These helpers are no longer present, so automated Acrobat-visibility analysis is unavailable.
* **Widget repair utilities.** iText 7 let us manipulate annotations via `PdfWidgetAnnotation` and resource dictionaries with
  relative ease. Re-implementing `SignatureWidgetRepairer` or `PostSignSanitizer` on top of iText 5's `PdfDictionary`/`PdfIndirectObject`
  primitives would be fragile and is out-of-scope for this cut.
* **Lazy form injection.** The old "Plan B" (injecting a row's fields on-demand before signing) used iText 7 forms/layout modules
  to rebuild table fragments. That feature has been removed—rows are expected to exist in the template created by
  `create-template`.

## Areas where iText 5 is known to be fragile

While the new implementation sticks to well-tested parts of iText 5, some limitations are worth noting:

* **Signature appearance generation** (`PdfSignatureAppearance`, `PdfTemplate`) in 5.5.6 is sensitive to fonts and encodings. The
  library lacks the font resource fallback logic added in iText 7, so CJK-heavy signatures may require manual `BaseFont`
  management.
* **DocMDP enforcement** relies on `PdfSignatureAppearance.setCertificationLevel`, which historically interacted poorly with
  incremental updates because `PdfStamper` keeps the certification dictionary in memory. Bugs fixed in iText 7's
  `PdfSigner.applyLtv()` / DocMDP handling never landed in 5.x, so custom DocMDP code should audit `PdfSignatureAppearance` and
  `PdfStamper` carefully.
* **Incremental updates** use `PdfStamper`'s append mode. Earlier 5.x releases had edge cases with reused `PdfReader`
  instances, unresolved XRef streams, and signature byte-range calculation. Always create fresh `PdfReader` instances per
  signing step and avoid manipulating the same `PdfStamper` across threads.
* **Lock dictionaries** (FieldMDP) must be crafted manually by editing `PdfDictionary` objects. iText 5 does not expose the
  high-level helpers introduced in iText 7's `PdfFormField`/`PdfSignatureFormField`, so any lock/permission logic has to be
  implemented with raw COS objects.

If your project depends on the removed workflows, consider keeping an iText 7-based toolchain for validation and repair while
using this branch for environments that mandate iText 5.5.6.
