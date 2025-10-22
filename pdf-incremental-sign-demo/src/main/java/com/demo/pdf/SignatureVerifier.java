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
import com.itextpdf.signatures.PdfSigner;
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
            PdfAcroForm form = PdfAcroForm.getAcroForm(document, false);
            List<String> names = form != null ? signatureUtil.getSignatureNames() : Collections.emptyList();
            System.out.println("[verify] DocMDP certified: " + DocMDPUtil.hasDocMDP(document));
            System.out.println("AcroForm signatures: " + names);
            int exitCode = 0;
            List<SignatureDetails> details = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
            for (String name : names) {
                PdfPKCS7 pkcs7 = PdfSigner.verifySignature(document, name);
                boolean valid = pkcs7 != null && pkcs7.verify();
                if (!valid) {
                    exitCode = 2;
                }
                PdfFormField sigField = form != null ? form.getField(name) : null;
                PdfDictionary sigDict = null;
                if (sigField != null && sigField.getValue() != null) {
                    sigDict = sigField.getValue().getAsDictionary();
                }
                PdfName filter = sigDict != null ? sigDict.getAsName(PdfName.Filter) : null;
                PdfName subFilter = sigDict != null ? sigDict.getAsName(PdfName.SubFilter) : null;
                System.out.println(name + " Filter=" + formatPdfName(filter)
                        + " SubFilter=" + formatPdfName(subFilter)
                        + " Valid=" + valid);
                if (pkcs7 != null) {
                    String subject = pkcs7.getSigningCertificate().getSubjectDN().getName();
                    Date signDate = pkcs7.getSignDate().getTime();
                    System.out.println(" Signer subject: " + subject);
                    System.out.println(" Sign date: " + sdf.format(signDate));
                    boolean coversWhole = signatureUtil.signatureCoversWholeDocument(name);
                    int revision = signatureUtil.getRevision(name);
                    int totalRevisions = signatureUtil.getTotalRevisions();
                    System.out.println(" Covers whole document: " + coversWhole);
                    System.out.println(" Valid at revision: " + valid + " (" + revision + "/" + totalRevisions + ")");
                }
                if (sigField != null && sigField.getPdfObject().containsKey(PdfName.Lock)) {
                    System.out.println(" FieldMDP lock:");
                    PdfDictionary lockDict = sigField.getPdfObject().getAsDictionary(PdfName.Lock);
                    com.itextpdf.kernel.pdf.PdfArray array = lockDict.getAsArray(PdfName.Fields);
                    PdfName action = lockDict.getAsName(PdfName.Action);
                    System.out.println("  Action: " + (action != null ? action.getValue() : "unknown"));
                    if (array != null) {
                        for (int i = 0; i < array.size(); i++) {
                            PdfString locked = array.getAsString(i);
                            if (locked == null) {
                                continue;
                            }
                            String fieldName = locked.toUnicodeString();
                            PdfFormField lockedField = form.getField(fieldName);
                            boolean readOnly = lockedField != null && lockedField.isReadOnly();
                            System.out.println("  - " + fieldName + " (readOnly=" + readOnly + ")");
                        }
                    }
                } else {
                    System.out.println(" No FieldMDP lock information available");
                }
                details.add(new SignatureDetails(name, filter, subFilter, valid));
            }
            return new SignatureReport(details, exitCode);
        }
    }

    private static String formatPdfName(PdfName name) {
        return name != null ? "/" + name.getValue() : "null";
    }

    public static final class SignatureReport {
        private final List<SignatureDetails> signatures;
        private final int exitCode;

        private SignatureReport(List<SignatureDetails> signatures, int exitCode) {
            this.signatures = Collections.unmodifiableList(new ArrayList<>(signatures));
            this.exitCode = exitCode;
        }

        public int totalSignatures() {
            return signatures.size();
        }

        public List<String> getFieldNames() {
            List<String> names = new ArrayList<>();
            for (SignatureDetails detail : signatures) {
                names.add(detail.getFieldName());
            }
            return Collections.unmodifiableList(names);
        }

        public int exitCode() {
            return exitCode;
        }

        public List<SignatureDetails> getSignatures() {
            return signatures;
        }

        public SignatureDetails getSignatureByName(String fieldName) {
            for (SignatureDetails detail : signatures) {
                if (detail.getFieldName().equals(fieldName)) {
                    return detail;
                }
            }
            return null;
        }
    }

    public static final class SignatureDetails {
        private final String fieldName;
        private final PdfName filter;
        private final PdfName subFilter;
        private final boolean valid;

        private SignatureDetails(String fieldName, PdfName filter, PdfName subFilter, boolean valid) {
            this.fieldName = fieldName;
            this.filter = filter;
            this.subFilter = subFilter;
            this.valid = valid;
        }

        public String getFieldName() {
            return fieldName;
        }

        public PdfName getFilter() {
            return filter;
        }

        public PdfName getSubFilter() {
            return subFilter;
        }

        public boolean isValid() {
            return valid;
        }
    }
}
