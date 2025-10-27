package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.security.PdfPKCS7;

import java.util.List;

/**
 * Minimal verification utility using iText 5.
 */
public final class SignatureVerifier {

    private SignatureVerifier() {
    }

    public static int verify(String pdfPath) throws Exception {
        DemoKeystoreUtil.ensureProvider();
        PdfReader reader = new PdfReader(pdfPath);
        try {
            AcroFields fields = reader.getAcroFields();
            List<String> names = fields.getSignatureNames();
            if (names.isEmpty()) {
                System.out.println("No signature fields found in " + pdfPath);
                return 1;
            }
            int exit = 0;
            for (String name : names) {
                PdfPKCS7 pkcs7 = fields.verifySignature(name);
                boolean coversDocument = fields.signatureCoversWholeDocument(name);
                boolean valid = pkcs7.verify();
                System.out.printf("Signature %s | valid=%s | coversWholeDocument=%s | subject=%s%n",
                        name,
                        valid,
                        coversDocument,
                        pkcs7.getSigningCertificate() != null ? pkcs7.getSigningCertificate().getSubjectDN() : "<unknown>");
                if (!valid) {
                    exit = 2;
                }
            }
            return exit;
        } finally {
            reader.close();
        }
    }
}
