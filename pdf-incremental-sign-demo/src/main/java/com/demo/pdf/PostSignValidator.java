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

    public static TailInfo strictTailCheck(Path pdf) throws IOException {
        return strictTailCheck(pdf, false);
    }

    public static TailInfo strictTailCheck(Path pdf, boolean strict) throws IOException {
        if (pdf == null) {
            throw new IllegalArgumentException("PDF path must not be null");
        }
        byte[] all = Files.readAllBytes(pdf);
        if (all.length < 16) {
            throw new IllegalStateException("PDF too small.");
        }

        TailInfo info = locateTailInternal(pdf, all, strict);
        ensureNoGarbageAfterEof(all, info.getEofOffset());
        return info;
    }

    public static TailInfo locateTail(Path pdf, boolean strict) throws IOException {
        if (pdf == null) {
            throw new IllegalArgumentException("PDF path must not be null");
        }
        byte[] all = Files.readAllBytes(pdf);
        if (all.length < 16) {
            throw new IllegalStateException("PDF too small.");
        }
        return locateTailInternal(pdf, all, strict);
    }

    private static TailInfo locateTailInternal(Path pdf, byte[] data, boolean strict) throws IOException {
        int eofIndex = locateEof(data);
        long declaredOffset = parseStartxref(data, eofIndex);
        TailInfo declared = tryParseTailAt(data, declaredOffset, eofIndex);
        if (declared.isOk()) {
            return declared;
        }

        TailInfo scanned = scanForLastXrefOrXrefStream(data, eofIndex);
        if (scanned.isOk()) {
            if (strict) {
                throw new IllegalStateException("startxref does not point to xref/xref-stream at declared offset "
                        + declaredOffset);
            }
            System.out.printf("[tail] WARNING: startxref declared %d but using scanned tail at %d (%s)%n",
                    declaredOffset, scanned.getActualOffset(), scanned.getType());
            return scanned.withDeclaredOffset(declaredOffset);
        }

        String probe = declared.getProbeSnippet();
        throw new IllegalStateException("startxref does not point to xref/xref-stream. Got: '" + probe + "'");
    }

    private static void ensureNoGarbageAfterEof(byte[] data, int eofIndex) {
        byte[] eofBytes = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        int eofEnd = eofIndex + eofBytes.length;
        for (int i = eofEnd; i < data.length; i++) {
            byte b = data[i];
            if (b != '\r' && b != '\n') {
                throw new IllegalStateException("Extra bytes found after %%EOF at offset " + (eofIndex + eofBytes.length));
            }
        }
    }

    private static int locateEof(byte[] data) {
        byte[] eofBytes = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        int eofIndex = lastIndexOf(data, eofBytes);
        if (eofIndex < 0) {
            throw new IllegalStateException("No %%EOF at tail.");
        }
        return eofIndex;
    }

    private static long parseStartxref(byte[] data, int eofIndex) {
        byte[] startxrefBytes = "startxref".getBytes(StandardCharsets.US_ASCII);
        int startxrefIndex = lastIndexOf(data, startxrefBytes, eofIndex);
        if (startxrefIndex < 0) {
            throw new IllegalStateException("startxref not found before %%EOF.");
        }

        int startLineEnd = findEol(data, startxrefIndex);
        if (startLineEnd < 0) {
            throw new IllegalStateException("Malformed startxref (no newline).");
        }
        int offsetLineStart = skipEol(data, startLineEnd);
        int offsetLineEnd = findEol(data, offsetLineStart);
        if (offsetLineEnd < 0) {
            throw new IllegalStateException("Malformed startxref (no number line).");
        }

        String numStr = new String(data, offsetLineStart, offsetLineEnd - offsetLineStart,
                StandardCharsets.US_ASCII).trim();
        long offset;
        try {
            offset = Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("startxref offset is not a number: '" + numStr + "'");
        }

        if (offset <= 0 || offset >= data.length) {
            throw new IllegalStateException("startxref offset out of range: " + offset);
        }
        return offset;
    }

    private static TailInfo tryParseTailAt(byte[] data, long offset, int eofIndex) {
        if (offset <= 0 || offset >= data.length) {
            return TailInfo.failure(offset, eofIndex, null, "offset-out-of-range");
        }
        int pos = (int) offset;
        int adjusted = skipWhitespaceForward(data, pos, data.length);
        if (adjusted >= 0) {
            pos = adjusted;
        }
        TailType type = identifyTailType(data, pos);
        if (type == null) {
            return TailInfo.failure(offset, eofIndex, null, extractProbe(data, offset));
        }
        return TailInfo.success(offset, eofIndex, pos, type, extractProbe(data, pos));
    }

    private static TailInfo scanForLastXrefOrXrefStream(byte[] data, int eofIndex) {
        final int window = 128 * 1024;
        int start = Math.max(0, data.length - window);
        int xrefTable = findLastXrefKeyword(data, start, data.length);
        int xrefStream = findLastXrefStreamObjectStart(data, start, data.length);

        if (xrefTable < 0 && xrefStream < 0) {
            return TailInfo.failure(-1L, eofIndex, null, "<none>");
        }

        if (xrefStream > xrefTable) {
            return TailInfo.success(-1L, eofIndex, xrefStream, TailType.XREF_STREAM, extractProbe(data, xrefStream));
        }

        return TailInfo.success(-1L, eofIndex, xrefTable, TailType.XREF_TABLE, extractProbe(data, xrefTable));
    }

    private static TailType identifyTailType(byte[] data, int pos) {
        if (pos < 0) {
            return null;
        }
        if (matchesKeyword(data, pos, "xref")) {
            int before = pos - 1;
            if (before < 0 || isWhitespace(data[before])) {
                return TailType.XREF_TABLE;
            }
        }
        int headerStart = findObjectHeaderStart(data, pos);
        if (headerStart >= 0) {
            String dict = extractDictionarySnippet(data, headerStart);
            if (dict != null) {
                String compact = dict.replaceAll("\\s+", "");
                if (compact.contains("/Type/XRef") || compact.contains("/Type/Xref") || compact.contains("/Type/xref")) {
                    return TailType.XREF_STREAM;
                }
            }
        }
        return null;
    }

    private static boolean matchesKeyword(byte[] data, int pos, String keyword) {
        byte[] bytes = keyword.getBytes(StandardCharsets.US_ASCII);
        if (pos + bytes.length > data.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (data[pos + i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static int findObjectHeaderStart(byte[] data, int startPos) {
        for (int i = startPos; i >= 0; i--) {
            if (matchesKeyword(data, i, "obj")) {
                int j = i - 1;
                while (j >= 0 && isWhitespace(data[j])) {
                    j--;
                }
                if (j < 0) {
                    continue;
                }
                while (j >= 0 && Character.isDigit((char) data[j])) {
                    j--;
                }
                if (j < 0 || !isWhitespace(data[j])) {
                    continue;
                }
                int genEnd = j;
                j--;
                while (j >= 0 && isWhitespace(data[j])) {
                    j--;
                }
                if (j < 0) {
                    continue;
                }
                while (j >= 0 && Character.isDigit((char) data[j])) {
                    j--;
                }
                if (j >= 0 && isWhitespace(data[j])) {
                    return j + 1;
                }
            }
        }
        return -1;
    }

    private static String extractDictionarySnippet(byte[] data, int headerStart) {
        int dictStart = indexOf(data, "<<".getBytes(StandardCharsets.US_ASCII), headerStart);
        if (dictStart < 0) {
            return null;
        }
        int depth = 0;
        for (int i = dictStart; i < data.length - 1; i++) {
            if (data[i] == '<' && data[i + 1] == '<') {
                depth++;
                i++;
            } else if (data[i] == '>' && data[i + 1] == '>') {
                depth--;
                i++;
                if (depth == 0) {
                    return new String(data, dictStart, (i + 1) - dictStart, StandardCharsets.US_ASCII);
                }
            }
        }
        return null;
    }

    private static int findLastXrefKeyword(byte[] data, int start, int end) {
        byte[] keyword = "xref".getBytes(StandardCharsets.US_ASCII);
        for (int i = end - keyword.length; i >= start; i--) {
            if (matchesKeyword(data, i, "xref")) {
                if (i - 1 < 0 || isWhitespace(data[i - 1])) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findLastXrefStreamObjectStart(byte[] data, int start, int end) {
        for (int i = end - 5; i >= start; i--) {
            if (data[i] == '/' && matchName(data, i, "/Type")) {
                int j = skipWhitespaceForward(data, i + 5, end);
                if (j < 0) {
                    continue;
                }
                if (matchName(data, j, "/XRef") || matchName(data, j, "/Xref") || matchName(data, j, "/xref")) {
                    int headerStart = findObjectHeaderStart(data, i);
                    if (headerStart >= 0) {
                        return headerStart;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean matchName(byte[] data, int pos, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        if (pos + bytes.length > data.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (data[pos + i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static int skipWhitespaceForward(byte[] data, int pos, int end) {
        int i = pos;
        while (i < end) {
            if (!isWhitespace(data[i])) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static boolean isWhitespace(byte b) {
        return b == '\r' || b == '\n' || b == '\t' || b == '\f' || b == ' ';
    }

    private static String extractProbe(byte[] data, long offset) {
        int pos = (int) Math.max(0, Math.min(data.length - 1, offset));
        int len = Math.min(64, data.length - pos);
        if (len <= 0) {
            return "";
        }
        return new String(data, pos, len, StandardCharsets.US_ASCII).replaceAll("\r", "\\r").replaceAll("\n", "\\n");
    }

    private static int indexOf(byte[] data, byte[] needle, int start) {
        if (needle.length == 0) {
            return start;
        }
        for (int i = Math.max(0, start); i <= data.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
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

    public enum TailType {
        XREF_TABLE,
        XREF_STREAM
    }

    public static final class TailInfo {
        private final boolean ok;
        private final long declaredOffset;
        private final long actualOffset;
        private final TailType type;
        private final String probeSnippet;
        private final int eofOffset;

        private TailInfo(boolean ok, long declaredOffset, long actualOffset,
                         TailType type, String probeSnippet, int eofOffset) {
            this.ok = ok;
            this.declaredOffset = declaredOffset;
            this.actualOffset = actualOffset;
            this.type = type;
            this.probeSnippet = probeSnippet;
            this.eofOffset = eofOffset;
        }

        static TailInfo success(long declaredOffset, int eofOffset, long actualOffset,
                                TailType type, String probeSnippet) {
            return new TailInfo(true, declaredOffset, actualOffset, type, probeSnippet, eofOffset);
        }

        static TailInfo failure(long declaredOffset, int eofOffset, TailType type, String probeSnippet) {
            return new TailInfo(false, declaredOffset, declaredOffset, type, probeSnippet, eofOffset);
        }

        TailInfo withDeclaredOffset(long newDeclared) {
            return new TailInfo(this.ok, newDeclared, this.actualOffset, this.type, this.probeSnippet, this.eofOffset);
        }

        public boolean isOk() {
            return ok;
        }

        public long getDeclaredOffset() {
            return declaredOffset;
        }

        public long getActualOffset() {
            return actualOffset;
        }

        public TailType getType() {
            return type;
        }

        public String getProbeSnippet() {
            return probeSnippet;
        }

        public int getEofOffset() {
            return eofOffset;
        }
    }
}
