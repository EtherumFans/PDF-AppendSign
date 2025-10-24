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
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.signatures.SignatureUtil;

import com.itextpdf.kernel.geom.Rectangle;

import java.nio.file.Path;
import java.util.List;

public class SignatureVerifier {

    public static int verify(String path) throws Exception {
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
                    if (!SignatureDiagnostics.rectanglesSimilar(result.getWidgetRect(), expectedRect)) {
                        throw new IllegalStateException("Signature widget rectangle mismatch for " + name);
                    }
                }

                String subject = result.getSigningCertificateSubject() != null
                        ? result.getSigningCertificateSubject()
                        : "<missing>";
                System.out.printf(" - %s | Filter=/%s, SubFilter=/%s, Valid=%s%n",
                        name,
                        result.getFilter().getValue(),
                        result.getSubFilter().getValue(),
                        result.isPkcs7Valid());
                System.out.println("     Signing cert subject: " + subject);
                System.out.println("     ByteRange=" + result.formatByteRange());
                boolean printable = (result.getWidgetFlags() & PdfAnnotation.PRINT) != 0;
                boolean hidden = (result.getWidgetFlags() & (PdfAnnotation.INVISIBLE
                        | PdfAnnotation.HIDDEN
                        | PdfAnnotation.NO_VIEW
                        | PdfAnnotation.TOGGLE_NO_VIEW)) != 0;
                System.out.println("     Widget PRINT=" + printable + ", INVISIBLE/HIDDEN/NOVIEW=" + hidden);
            }
            return 0;
        }
    }

    static void dumpCatalogAndAcroForm(String pdf) throws Exception {
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
            System.err.println("Usage: java ... SignatureVerifier <pdf>");
            System.exit(2);
        }
        int code = verify(args[0]);
        System.exit(code);
    }
}
