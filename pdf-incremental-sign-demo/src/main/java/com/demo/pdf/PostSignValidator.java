package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Performs strict sanity checks on a freshly signed PDF revision to ensure Adobe Acrobat will list the signature.
 */
public final class PostSignValidator {

    private PostSignValidator() {
    }

    public static void validate(String dest, String fname) throws Exception {
        if (dest == null || dest.isBlank()) {
            throw new IllegalArgumentException("Destination path must not be blank");
        }
        if (fname == null || fname.isBlank()) {
            throw new IllegalArgumentException("Expected field name must not be blank");
        }

        try (RandomAccessFile raf = new RandomAccessFile(dest, "r")) {
            byte[] head = new byte[8];
            raf.readFully(head);
            String header = new String(head, StandardCharsets.US_ASCII);
            if (!header.startsWith("%PDF-")) {
                throw new IllegalStateException("PDF header not at byte 0 (BOM/garbage): Adobe may ignore signatures.");
            }
        }

        try (PdfDocument pdf = new PdfDocument(new PdfReader(dest))) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                throw new IllegalStateException("No AcroForm present.");
            }

            SignatureUtil su = new SignatureUtil(pdf);
            List<String> names = su.getSignatureNames();
            if (names == null || names.isEmpty()) {
                throw new IllegalStateException("No field-bound signatures.");
            }
            if (!names.contains(fname)) {
                throw new IllegalStateException("Missing expected signature: " + fname);
            }

            PdfFormField field = acro.getField(fname);
            if (field == null) {
                throw new IllegalStateException("Signature field missing: " + fname);
            }
            PdfDictionary fDict = field.getPdfObject();
            PdfDictionary sig = fDict.getAsDictionary(PdfName.V);
            if (sig == null) {
                throw new IllegalStateException("Field /V is null (no /Sig).");
            }

            PdfName filter = sig.getAsName(PdfName.Filter);
            if (!PdfName.Adobe_PPKLite.equals(filter)) {
                throw new IllegalStateException("Filter != /Adobe.PPKLite: " + filter);
            }

            PdfName sub = sig.getAsName(PdfName.SubFilter);
            if (!(PdfName.Adbe_pkcs7_detached.equals(sub) || PdfName.ETSI_CAdES_DETACHED.equals(sub))) {
                throw new IllegalStateException("SubFilter not CMS/CAdES: " + sub);
            }

            PdfArray br = sig.getAsArray(PdfName.ByteRange);
            if (br == null || br.size() != 4) {
                throw new IllegalStateException("Invalid /ByteRange: " + br);
            }
            PdfNumber start = br.getAsNumber(0);
            if (start == null || start.longValue() != 0L) {
                throw new IllegalStateException("Invalid /ByteRange: " + br);
            }
            for (int i = 1; i < 4; i++) {
                PdfNumber num = br.getAsNumber(i);
                if (num == null || num.longValue() < 0L) {
                    throw new IllegalStateException("Invalid /ByteRange: " + br);
                }
            }

            PdfString contents = sig.getAsString(PdfName.Contents);
            if (contents == null || (contents.getValueBytes().length % 2) != 0) {
                throw new IllegalStateException("/Contents must be even-length hex.");
            }

            PdfPKCS7 pk = su.readSignatureData(fname);
            if (pk.getSigningCertificate() == null) {
                throw new IllegalStateException("Signing certificate missing in PKCS#7 Contents (cannot verify).");
            }
            if (!pk.verifySignatureIntegrityAndAuthenticity()) {
                throw new IllegalStateException("PKCS7 integrity/authenticity failed.");
            }

            List<PdfWidgetAnnotation> widgets = field.getWidgets();
            if (widgets == null || widgets.isEmpty()) {
                throw new IllegalStateException("Signature field has no widget.");
            }
            PdfWidgetAnnotation w = widgets.get(0);
            if (w.getPage() == null) {
                throw new IllegalStateException("Widget not attached to any page.");
            }
            PdfWidgetUtil.ensureWidgetInAnnots(w.getPage(), w, fname);
            PdfNumber flagNumber = w.getPdfObject().getAsNumber(PdfName.F);
            int flags = flagNumber != null ? flagNumber.intValue() : w.getFlags();
            if ((flags & PdfAnnotation.PRINT) == 0) {
                throw new IllegalStateException("Widget missing PRINT flag.");
            }
            if ((flags & (PdfAnnotation.HIDDEN | PdfAnnotation.NO_VIEW | PdfAnnotation.INVISIBLE | PdfAnnotation.TOGGLE_NO_VIEW)) != 0) {
                throw new IllegalStateException("Widget hidden/NOVIEW.");
            }
        }
    }
}
