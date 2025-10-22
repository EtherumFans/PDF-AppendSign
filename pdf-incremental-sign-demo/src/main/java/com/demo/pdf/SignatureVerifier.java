package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SignatureVerifier {

    private SignatureVerifier() {
    }

    public static SignatureReport verify(String pdfPath) throws Exception {
        DemoKeystoreUtil.ensureProvider();
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument document = new PdfDocument(reader)) {
            SignatureUtil signatureUtil = new SignatureUtil(document);
            List<String> names = signatureUtil.getSignatureNames();
            System.out.println("[verify] DocMDP certified: " + DocMDPUtil.hasDocMDP(document));
            System.out.println("[verify] Signatures found: " + names.size());
            PdfAcroForm form = PdfAcroForm.getAcroForm(document, true);
            int exitCode = 0;
            for (String name : names) {
                System.out.println("\n=== Signature: " + name + " ===");
                PdfPKCS7 pkcs7 = signatureUtil.readSignatureData(name);
                boolean coversWhole = signatureUtil.signatureCoversWholeDocument(name);
                boolean valid = pkcs7.verifySignatureIntegrityAndAuthenticity();
                String subject = pkcs7.getSigningCertificate().getSubjectDN().getName();
                Date signDate = pkcs7.getSignDate().getTime();
                int revision = signatureUtil.getRevision(name);
                int totalRevisions = signatureUtil.getTotalRevisions();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
                PdfDictionary signatureDictionary = signatureUtil.getSignatureDictionary(name);
                PdfName subFilter = signatureDictionary != null ? signatureDictionary.getAsName(PdfName.SubFilter) : null;
                System.out.println("SubFilter: " + (subFilter != null ? subFilter.getValue() : "unknown"));
                System.out.println("Signer subject: " + subject);
                System.out.println("Sign date: " + sdf.format(signDate));
                System.out.println("Covers whole document: " + coversWhole);
                System.out.println("Valid at revision: " + valid + " (" + revision + "/" + totalRevisions + ")");
                if (!valid) {
                    exitCode = 2;
                }
                PdfFormField sigField = form.getField(name);
                if (sigField != null && sigField.getPdfObject().containsKey(PdfName.Lock)) {
                    System.out.println("FieldMDP lock:");
                    PdfDictionary lockDict = sigField.getPdfObject().getAsDictionary(PdfName.Lock);
                    com.itextpdf.kernel.pdf.PdfArray array = lockDict.getAsArray(PdfName.Fields);
                    PdfName action = lockDict.getAsName(PdfName.Action);
                    System.out.println(" Action: " + (action != null ? action.getValue() : "unknown"));
                    if (array != null) {
                        for (int i = 0; i < array.size(); i++) {
                            PdfString locked = array.getAsString(i);
                            if (locked == null) {
                                continue;
                            }
                            String fieldName = locked.toUnicodeString();
                            PdfFormField lockedField = form.getField(fieldName);
                            boolean readOnly = lockedField != null && lockedField.isReadOnly();
                            System.out.println(" - " + fieldName + " (readOnly=" + readOnly + ")");
                        }
                    }
                } else {
                    System.out.println("No FieldMDP lock information available");
                }
            }
            return new SignatureReport(names, exitCode);
        }
    }

    public static final class SignatureReport {
        private final List<String> fieldNames;
        private final int exitCode;

        private SignatureReport(List<String> names, int exitCode) {
            this.fieldNames = Collections.unmodifiableList(new ArrayList<>(names));
            this.exitCode = exitCode;
        }

        public int totalSignatures() {
            return fieldNames.size();
        }

        public List<String> getFieldNames() {
            return fieldNames;
        }

        public int exitCode() {
            return exitCode;
        }
    }
}
