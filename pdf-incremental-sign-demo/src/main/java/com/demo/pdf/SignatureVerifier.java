package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.SignatureUtil;

import java.util.Collections;
import java.util.List;

public class SignatureVerifier {

    public static int verify(String path) throws Exception {
        try (PdfDocument pdf = new PdfDocument(new PdfReader(path))) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            SignatureUtil su = new SignatureUtil(pdf);

            List<String> names = (acro != null) ? su.getSignatureNames() : Collections.emptyList();
            System.out.println("PDF: " + path);
            System.out.println("AcroForm signatures: " + names.size() + " -> " + names);

            if (names.isEmpty()) {
                System.out.println("No field-bound signatures found (Adobe panel will be empty).");
                return 2; // non-zero so CI/CLI can catch it
            }

            int invalid = 0;
            for (String name : names) {
                PdfPKCS7 pk = PdfSigner.verifySignature(pdf, name); // <- correct API
                boolean valid = pk.verifySignatureIntegrityAndAuthenticity();

                PdfFormField field = acro.getField(name);
                PdfDictionary sigDict = (field != null && field.getValue() != null)
                        ? field.getValue().getAsDictionary()
                        : null;

                PdfName filter = sigDict != null ? sigDict.getAsName(PdfName.Filter) : null;
                PdfName subFilter = sigDict != null ? sigDict.getAsName(PdfName.SubFilter) : null;

                System.out.printf(" - %s | Filter=%s, SubFilter=%s, Valid=%s, SignDate=%s, SubjectCN=%s%n",
                        name,
                        (filter != null ? filter.getValue() : "null"),
                        (subFilter != null ? subFilter.getValue() : "null"),
                        valid,
                        pk.getSignDate(),
                        pk.getSigningCertificate() != null
                                ? pk.getSigningCertificate().getSubjectX500Principal().getName()
                                : "N/A"
                );
                if (!valid) invalid++;
            }
            return invalid == 0 ? 0 : 3;
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
