package com.demo.pdf;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.security.PdfPKCS7;

import java.util.List;

/**
 * Lightweight signature verification helper for the iText 5 branch.
 */
public final class SignatureVerifier {

    private SignatureVerifier() {
    }

    public static int verify(String path) throws Exception {
        DocMDPUtil.ensureProvider();
        try (PdfReader reader = new PdfReader(path)) {
            AcroFields form = reader.getAcroFields();
            List<String> names = form.getSignatureNames();
            if (names.isEmpty()) {
                System.out.println("[verify] No digital signatures found.");
                return 1;
            }
            int exitCode = 0;
            for (String name : names) {
                System.out.println("[verify] Signature name: " + name);
                PdfPKCS7 pkcs7 = form.verifySignature(name);
                boolean verifies = pkcs7.verify();
                System.out.println("  - Subject: " + pkcs7.getSigningCertificate().getSubjectDN());
                System.out.println("  - Signed on: " + pkcs7.getSignDate().getTime());
                System.out.println("  - Integrity check: " + (verifies ? "OK" : "FAILED"));
                if (!verifies) {
                    exitCode = 2;
                }
            }
            PdfDictionary perms = reader.getCatalog().getAsDict(PdfName.PERMS);
            if (perms != null && perms.contains(PdfName.DOCMDP)) {
                System.out.println("[verify] DocMDP certification present.");
            } else {
                System.out.println("[verify] DocMDP certification not present.");
            }
            System.out.println("[verify] FieldMDP reachability diagnostics are unavailable in iText 5.5.6.");
            return exitCode;
        }
    }
}
