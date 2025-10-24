package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.SignatureUtil;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

            SignatureDiagnostics.SignatureCheckResult result =
                    SignatureDiagnostics.inspectSignature(Path.of(dest), pdf, su, acro, fname);

            if (!result.isAdobeVisibleMinimalStructure()) {
                throw new IllegalStateException("Signature minimal structure invalid: "
                        + String.join("; ", result.getAdobeVisibilityIssues()));
            }
            if (!result.isPkcs7Parsed()) {
                String note = result.getPkcs7Error() != null ? (": " + result.getPkcs7Error()) : ".";
                throw new IllegalStateException("Unable to parse PKCS#7" + note);
            }
            if (!result.isPkcs7Valid()) {
                String note = result.getPkcs7Error() != null ? (": " + result.getPkcs7Error()) : ".";
                throw new IllegalStateException("PKCS7 integrity/authenticity failed" + note);
            }
            if (result.getSigningCertificateSubject() == null) {
                throw new IllegalStateException("Signing certificate subject missing in PKCS#7 Contents.");
            }
            if (result.getPageNumber() < 1) {
                throw new IllegalStateException("Signature widget is not attached to a valid page.");
            }
            if (result.getWidgetRect() == null) {
                throw new IllegalStateException("Signature widget rectangle unavailable.");
            }
            if (!result.isWidgetPrintable()) {
                throw new IllegalStateException("Signature widget " + fname + " is not printable (PRINT flag missing)");
            }
            if (result.isWidgetHidden()) {
                throw new IllegalStateException("Signature widget " + fname + " is hidden (INVISIBLE/HIDDEN/NOVIEW)");
            }
            if (!result.isWidgetInAnnots()) {
                throw new IllegalStateException("Signature widget " + fname + " not listed in page /Annots array");
            }
            if (!result.hasWidgetAppearance()) {
                throw new IllegalStateException("Signature widget " + fname + " has no normal appearance (/AP.N)");
            }
        }
    }

    public static void strictTailCheck(Path pdf) throws IOException {
        if (pdf == null) {
            throw new IllegalArgumentException("PDF path must not be null");
        }
        byte[] all = Files.readAllBytes(pdf);
        if (all.length < 16) {
            throw new IllegalStateException("PDF too small.");
        }

        byte[] eofBytes = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        int eofIndex = lastIndexOf(all, eofBytes);
        if (eofIndex < 0) {
            throw new IllegalStateException("No %%EOF at tail.");
        }

        byte[] startxrefBytes = "startxref".getBytes(StandardCharsets.US_ASCII);
        int startxrefIndex = lastIndexOf(all, startxrefBytes, eofIndex);
        if (startxrefIndex < 0) {
            throw new IllegalStateException("startxref not found before %%EOF.");
        }

        int startLineEnd = findEol(all, startxrefIndex);
        if (startLineEnd < 0) {
            throw new IllegalStateException("Malformed startxref (no newline).");
        }
        int offsetLineStart = skipEol(all, startLineEnd);
        int offsetLineEnd = findEol(all, offsetLineStart);
        if (offsetLineEnd < 0) {
            throw new IllegalStateException("Malformed startxref (no number line).");
        }

        String numStr = new String(all, offsetLineStart, offsetLineEnd - offsetLineStart,
                StandardCharsets.US_ASCII).trim();
        long offset;
        try {
            offset = Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("startxref offset is not a number: '" + numStr + "'");
        }

        if (offset <= 0 || offset >= all.length) {
            throw new IllegalStateException("startxref offset out of range: " + offset);
        }

        int probeLen = (int) Math.min(32L, all.length - offset);
        String probe = new String(all, (int) offset, probeLen, StandardCharsets.US_ASCII);
        if (!(probe.startsWith("xref") || probe.contains("/XRef"))) {
            throw new IllegalStateException("startxref does not point to xref/xref-stream. Got: '" + probe + "'");
        }

        int eofEnd = eofIndex + eofBytes.length;
        for (int i = eofEnd; i < all.length; i++) {
            byte b = all[i];
            if (b != '\r' && b != '\n') {
                throw new IllegalStateException(
                        "Extra bytes found after %%EOF at offset " + (eofIndex + eofBytes.length));
            }
        }
    }

    private static int lastIndexOf(byte[] haystack, byte[] needle) {
        for (int i = haystack.length - needle.length; i >= 0; i--) {
            if (matchesAt(haystack, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(byte[] haystack, byte[] needle, int before) {
        int start = Math.min(before, haystack.length) - needle.length;
        for (int i = start; i >= 0; i--) {
            if (matchesAt(haystack, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(byte[] haystack, byte[] needle, int pos) {
        if (pos < 0 || pos + needle.length > haystack.length) {
            return false;
        }
        for (int i = 0; i < needle.length; i++) {
            if (haystack[pos + i] != needle[i]) {
                return false;
            }
        }
        return true;
    }

    private static int findEol(byte[] data, int from) {
        for (int i = from; i < data.length; i++) {
            byte b = data[i];
            if (b == '\n' || b == '\r') {
                return i;
            }
        }
        return -1;
    }

    private static int skipEol(byte[] data, int eolIndex) {
        int i = eolIndex;
        if (i < data.length && (data[i] == '\r' || data[i] == '\n')) {
            byte first = data[i++];
            if (first == '\r' && i < data.length && data[i] == '\n') {
                i++;
            }
        }
        return i;
    }
}
