package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SignatureDiagnostics {

    private static final PdfName SUBFILTER_ADBE_PKCS7_DETACHED = new PdfName("adbe.pkcs7.detached");
    private static final PdfName SUBFILTER_ETSI_CADES_DETACHED = new PdfName("ETSI.CAdES.detached");

    private static final Set<PdfName> ALLOWED_SUBFILTERS = new HashSet<>(Arrays.asList(
            PdfName.Adbe_pkcs7_detached,
            SUBFILTER_ADBE_PKCS7_DETACHED,
            SUBFILTER_ETSI_CADES_DETACHED
    ));

    private static final float RECT_TOLERANCE = 0.5f;

    private SignatureDiagnostics() {
    }

    public static SignatureCheckResult inspectSignature(Path pdfPath, PdfDocument pdf, SignatureUtil util,
                                                         PdfAcroForm acro, String sigName) throws Exception {
        Objects.requireNonNull(pdfPath, "pdfPath");
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(util, "util");
        Objects.requireNonNull(sigName, "sigName");

        if (acro == null) {
            throw new IllegalStateException("No AcroForm present in document");
        }

        PdfFormField field = acro.getField(sigName);
        if (field == null) {
            throw new IllegalStateException("AcroForm is missing signature field " + sigName);
        }

        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            throw new IllegalStateException("Signature field " + sigName + " has no widget annotations");
        }

        PdfWidgetAnnotation widget = selectWidget(widgets, pdf, sigName);
        PdfPage widgetPage = widget.getPage();
        if (widgetPage == null) {
            throw new IllegalStateException("Signature widget for " + sigName + " has no page reference");
        }
        int pageNumber = pdf.getPageNumber(widgetPage);
        if (pageNumber < 1) {
            throw new IllegalStateException("Signature widget for " + sigName + " has invalid page index: " + pageNumber);
        }

        PdfWidgetUtil.ensureWidgetInAnnots(widgetPage, widget, sigName);

        Rectangle widgetRect = widget.getRectangle().toRectangle();
        if (widgetRect.getWidth() <= 0 || widgetRect.getHeight() <= 0) {
            throw new IllegalStateException("Signature widget for " + sigName + " has invalid rectangle");
        }

        int flags = widget.getFlags();
        if ((flags & PdfAnnotation.PRINT) == 0) {
            throw new IllegalStateException("Signature widget " + sigName + " is not printable (missing PRINT flag)");
        }
        if ((flags & (PdfAnnotation.HIDDEN | PdfAnnotation.INVISIBLE | PdfAnnotation.TOGGLE_NO_VIEW | PdfAnnotation.NO_VIEW)) != 0) {
            throw new IllegalStateException("Signature widget " + sigName + " is hidden or not viewable");
        }

        PdfDictionary fieldDict = field.getPdfObject();
        PdfDictionary sigDict = fieldDict != null ? fieldDict.getAsDictionary(PdfName.V) : null;
        if (sigDict == null) {
            throw new IllegalStateException("Field /V is null for signature " + sigName);
        }

        PdfName type = sigDict.getAsName(PdfName.Type);
        if (!PdfName.Sig.equals(type)) {
            String actual = type != null ? "/" + type.getValue() : "null";
            throw new IllegalStateException("Field /V Type not /Sig for " + sigName + ": " + actual);
        }

        PdfName filter = sigDict.getAsName(PdfName.Filter);
        if (filter == null || !PdfName.Adobe_PPKLite.equals(filter)) {
            String actual = filter != null ? "/" + filter.getValue() : "null";
            throw new IllegalStateException("Signature Filter is not /Adobe.PPKLite for " + sigName + ": " + actual);
        }

        PdfName subFilter = sigDict.getAsName(PdfName.SubFilter);
        if (subFilter == null || !ALLOWED_SUBFILTERS.contains(subFilter)) {
            String actual = subFilter != null ? "/" + subFilter.getValue() : "null";
            throw new IllegalStateException("Signature SubFilter not CMS/CAdES for " + sigName + ": " + actual);
        }

        PdfArray byteRange = sigDict.getAsArray(PdfName.ByteRange);
        if (byteRange == null || byteRange.size() != 4) {
            throw new IllegalStateException("Invalid /ByteRange for signature " + sigName + ": " + byteRange);
        }
        long[] br = new long[4];
        for (int i = 0; i < 4; i++) {
            PdfNumber num = byteRange.getAsNumber(i);
            if (num == null) {
                throw new IllegalStateException("/ByteRange element " + i + " missing for signature " + sigName);
            }
            br[i] = num.longValue();
        }
        if (br[0] != 0L) {
            throw new IllegalStateException("/ByteRange must start at 0 for signature " + sigName + ": " + byteRange);
        }
        if (br[1] < 0 || br[2] < 0 || br[3] < 0) {
            throw new IllegalStateException("Negative value in /ByteRange for signature " + sigName + ": " + byteRange);
        }
        long fileLength = Files.size(pdfPath);
        if (br[1] > fileLength || br[2] + br[3] > fileLength) {
            throw new IllegalStateException("/ByteRange exceeds file length for signature " + sigName + ": " + byteRange);
        }
        if (br[2] < br[1]) {
            throw new IllegalStateException("/ByteRange offsets overlap for signature " + sigName + ": " + byteRange);
        }

        PdfString contents = sigDict.getAsString(PdfName.Contents);
        if (contents == null) {
            throw new IllegalStateException("/Contents missing for signature " + sigName);
        }
        if (!contents.isHexWriting()) {
            throw new IllegalStateException("/Contents must be a hex string for signature " + sigName);
        }
        if ((contents.getValueBytes().length & 1) != 0) {
            throw new IllegalStateException("/Contents must have even length for signature " + sigName);
        }

        PdfPKCS7 pkcs7 = util.readSignatureData(sigName);
        if (pkcs7 == null) {
            throw new IllegalStateException("Unable to parse PKCS7 for signature " + sigName);
        }
        Certificate signingCert = pkcs7.getSigningCertificate();
        if (signingCert == null) {
            throw new IllegalStateException("Signing certificate missing in PKCS#7 Contents for " + sigName);
        }
        if (!pkcs7.verifySignatureIntegrityAndAuthenticity()) {
            throw new IllegalStateException("PKCS7 invalid for signature " + sigName);
        }
        String subject = extractSubject(signingCert);

        return new SignatureCheckResult(sigName, filter, subFilter, true, br, pageNumber, new Rectangle(widgetRect), flags, subject);
    }

    private static PdfWidgetAnnotation selectWidget(List<PdfWidgetAnnotation> widgets, PdfDocument pdf, String sigName) {
        for (PdfWidgetAnnotation widget : widgets) {
            if (widget == null) {
                continue;
            }
            PdfPage page = widget.getPage();
            if (page != null) {
                return widget;
            }
        }
        throw new IllegalStateException("Signature field " + sigName + " is not placed on any page");
    }

    public static boolean rectanglesSimilar(Rectangle a, Rectangle b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.getX() - b.getX()) <= RECT_TOLERANCE
                && Math.abs(a.getY() - b.getY()) <= RECT_TOLERANCE
                && Math.abs(a.getWidth() - b.getWidth()) <= RECT_TOLERANCE
                && Math.abs(a.getHeight() - b.getHeight()) <= RECT_TOLERANCE;
    }

    public static int extractRowIndex(String signatureName) {
        if (signatureName == null) {
            return -1;
        }
        if (!signatureName.startsWith("sig_row_")) {
            return -1;
        }
        String suffix = signatureName.substring("sig_row_".length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static final class SignatureCheckResult {
        private final String name;
        private final PdfName filter;
        private final PdfName subFilter;
        private final boolean pkcs7Valid;
        private final long[] byteRange;
        private final int pageNumber;
        private final Rectangle widgetRect;
        private final int widgetFlags;
        private final String signingCertificateSubject;

        SignatureCheckResult(String name, PdfName filter, PdfName subFilter, boolean pkcs7Valid,
                              long[] byteRange, int pageNumber, Rectangle widgetRect, int widgetFlags,
                              String signingCertificateSubject) {
            this.name = name;
            this.filter = filter;
            this.subFilter = subFilter;
            this.pkcs7Valid = pkcs7Valid;
            this.byteRange = byteRange;
            this.pageNumber = pageNumber;
            this.widgetRect = widgetRect;
            this.widgetFlags = widgetFlags;
            this.signingCertificateSubject = signingCertificateSubject;
        }

        public String getName() {
            return name;
        }

        public PdfName getFilter() {
            return filter;
        }

        public PdfName getSubFilter() {
            return subFilter;
        }

        public boolean isPkcs7Valid() {
            return pkcs7Valid;
        }

        public long[] getByteRange() {
            return byteRange;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public Rectangle getWidgetRect() {
            return widgetRect;
        }

        public int getWidgetFlags() {
            return widgetFlags;
        }

        public String getSigningCertificateSubject() {
            return signingCertificateSubject;
        }

        public String formatByteRange() {
            return String.format("[%d, %d, %d, %d]", byteRange[0], byteRange[1], byteRange[2], byteRange[3]);
        }
    }

    private static String extractSubject(Certificate certificate) {
        if (certificate instanceof X509Certificate) {
            X509Certificate x509 = (X509Certificate) certificate;
            if (x509.getSubjectX500Principal() != null) {
                return x509.getSubjectX500Principal().getName();
            }
            if (x509.getSubjectDN() != null) {
                return x509.getSubjectDN().getName();
            }
        }
        return certificate.toString();
    }
}
