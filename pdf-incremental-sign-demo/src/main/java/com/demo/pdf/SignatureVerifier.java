package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.signatures.SignatureUtil;

import com.itextpdf.kernel.geom.Rectangle;

import java.nio.file.Path;
import java.util.List;

public class SignatureVerifier {

    public static int verify(String path) throws Exception {
        return runVerification(path, false);
    }

    public static int deepVerify(String path) throws Exception {
        return runVerification(path, true);
    }

    private static int runVerification(String path, boolean deepMode) throws Exception {
        DemoKeystoreUtil.ensureProvider();
        Path pdfPath = Path.of(path);
        PdfSanityUtil.requireHeader(pdfPath);
        PostSignValidator.strictTailCheck(pdfPath);
        try (PdfDocument pdf = new PdfDocument(new PdfReader(path))) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                throw new IllegalStateException("No AcroForm present in PDF: " + path);
            }
            SignatureUtil su = new SignatureUtil(pdf);

            List<String> names = su.getSignatureNames();
            System.out.println("PDF: " + path);
            System.out.println("AcroForm signatures: " + names.size() + " -> " + names);

            if (names.isEmpty()) {
                throw new IllegalStateException("No field-bound signatures found (Adobe panel will be empty).");
            }

            for (String name : names) {
                SignatureDiagnostics.SignatureCheckResult result =
                        SignatureDiagnostics.inspectSignature(pdfPath, pdf, su, acro, name);

                int rowIndex = SignatureDiagnostics.extractRowIndex(name);
                if (rowIndex > 0) {
                    Rectangle expectedRect = LayoutUtil.sigRectForRow(pdf, result.getPageNumber(), rowIndex);
                    if (result.getWidgetRect() != null
                            && !SignatureDiagnostics.rectanglesSimilar(result.getWidgetRect(), expectedRect)) {
                        throw new IllegalStateException("Signature widget rectangle mismatch for " + name);
                    }
                }

                String subject = result.getSigningCertificateSubject() != null
                        ? result.getSigningCertificateSubject()
                        : "<missing>";
                String filterValue = result.getFilter() != null ? result.getFilter().getValue() : "<null>";
                String subFilterValue = result.getSubFilter() != null ? result.getSubFilter().getValue() : "<null>";
                System.out.printf(" - %s | Filter=/%s, SubFilter=/%s%n",
                        name,
                        filterValue,
                        subFilterValue);
                boolean brShapeOk = result.isByteRangeShapeOk() && result.isByteRangeOffsetsOk();
                boolean contHexOk = result.isContentsHex() && result.isContentsEvenLength();
                System.out.printf("     Adobe-visible(minimal-structure): %s (brShape=%s, contHex=%s, coverage=%s, filter=%s)%n",
                        result.isAdobeVisibleMinimalStructure(),
                        brShapeOk,
                        contHexOk,
                        result.isByteRangeCoverageOk(),
                        result.isFilterAllowed());
                if (!result.isAdobeVisibleMinimalStructure()) {
                    for (String reason : result.getAdobeVisibilityIssues()) {
                        System.out.println("       - " + reason);
                    }
                }
                System.out.printf("     PKCS7 parsed=%s, Valid=%s%n", result.isPkcs7Parsed(), result.isPkcs7Valid());
                if (result.getPkcs7Error() != null) {
                    System.out.println("       PKCS7 note: " + result.getPkcs7Error());
                }
                System.out.println("     Signing cert subject: " + subject);
                System.out.println("     ByteRange=" + result.formatByteRange()
                        + ", ContentsHexLen=" + result.getContentsHexLength());
                System.out.println("     Widget PRINT=" + result.isWidgetPrintable()
                        + ", INVISIBLE/HIDDEN/NOVIEW=" + result.isWidgetHidden());
                System.out.println("     Widget page=" + result.getPageNumber()
                        + ", rect=" + result.getWidgetRect()
                        + ", AP(N)=" + result.hasWidgetAppearance()
                        + ", inAnnots=" + result.isWidgetInAnnots());
                if (deepMode) {
                    String verdict = result.isAdobeVisibleMinimalStructure()
                            ? "WILL be shown by Acrobat"
                            : "WILL NOT be shown by Acrobat";
                    System.out.println("     => This signature " + verdict + " (based on minimal structure checks)");
                }
            }
            return 0;
        }
    }

    private static void dumpCatalogAndAcroForm(String pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            PdfDictionary root = doc.getCatalog().getPdfObject();
            if (root == null) {
                System.out.println("Catalog root object missing");
                return;
            }
            if (root.getIndirectReference() != null) {
                System.out.println("Root obj#: " + root.getIndirectReference().getObjNumber());
            } else {
                System.out.println("Root obj#: <direct>");
            }
            PdfDictionary acro = root.getAsDictionary(PdfName.AcroForm);
            if (acro == null) {
                System.out.println("Catalog has NO /AcroForm");
                return;
            }
            if (acro.getIndirectReference() != null) {
                System.out.println("AcroForm obj#: " + acro.getIndirectReference().getObjNumber());
            } else {
                System.out.println("AcroForm obj#: <direct>");
            }
            PdfArray fields = acro.getAsArray(PdfName.Fields);
            System.out.println("AcroForm.Fields count: " + (fields == null ? 0 : fields.size()));
            if (fields != null) {
                for (int i = 0; i < fields.size(); i++) {
                    PdfDictionary f = fields.getAsDictionary(i);
                    if (f == null) {
                        System.out.printf("  [%d] <non-dictionary field entry>%n", i);
                        continue;
                    }
                    PdfName ft = f.getAsName(PdfName.FT);
                    PdfString t = f.getAsString(PdfName.T);
                    if (f.getIndirectReference() != null) {
                        System.out.printf("  [%d] FT=%s, T=%s, obj#=%d%n", i, ft, t,
                                f.getIndirectReference().getObjNumber());
                    } else {
                        System.out.printf("  [%d] FT=%s, T=%s, obj#=<direct>%n", i, ft, t);
                    }
                    PdfObject v = f.get(PdfName.V);
                    if (v != null && v.isDictionary()) {
                        PdfDictionary sig = (PdfDictionary) v;
                        System.out.printf("      /V Type=%s, Filter=%s, SubFilter=%s%n",
                                sig.getAsName(PdfName.Type),
                                sig.getAsName(PdfName.Filter),
                                sig.getAsName(PdfName.SubFilter));
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage:");
            System.err.println("  java ... SignatureVerifier <pdf>");
            System.err.println("  java ... SignatureVerifier dump-acroform <pdf>");
            System.err.println("  java ... SignatureVerifier deep-verify <pdf>");
            System.exit(2);
        }

        if (args.length == 2 && "dump-acroform".equals(args[0])) {
            dumpCatalogAndAcroForm(args[1]);
            return;
        }

        if (args.length == 2 && "deep-verify".equals(args[0])) {
            int code = deepVerify(args[1]);
            System.exit(code);
        }

        int code = verify(args[0]);
        System.exit(code);
    }
}
