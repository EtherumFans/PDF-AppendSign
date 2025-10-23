package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.SignatureUtil;

import com.itextpdf.kernel.geom.Rectangle;

import java.nio.file.Path;
import java.util.List;

public class SignatureVerifier {

    public static int verify(String path) throws Exception {
        DemoKeystoreUtil.ensureProvider();
        Path pdfPath = Path.of(path);
        PdfSanityUtil.requireHeader(pdfPath);
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
            }
            return 0;
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
