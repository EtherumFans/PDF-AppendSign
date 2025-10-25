package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

        PdfWidgetAnnotation widget = selectWidget(field.getWidgets());
        PdfPage widgetPage = widget != null ? widget.getPage() : null;
        int pageNumber = widgetPage != null ? pdf.getPageNumber(widgetPage) : -1;

        Rectangle widgetRect = null;
        int flags = 0;
        boolean widgetPrintable = false;
        boolean widgetHidden = false;
        boolean widgetInAnnots = false;
        boolean widgetHasAppearance = false;

        if (widget != null) {
            if (widget.getRectangle() != null) {
                widgetRect = widget.getRectangle().toRectangle();
            }
            flags = widget.getFlags();
            widgetPrintable = (flags & PdfAnnotation.PRINT) != 0;
            widgetHidden = (flags & (PdfAnnotation.INVISIBLE | PdfAnnotation.HIDDEN
                    | PdfAnnotation.NO_VIEW | PdfAnnotation.TOGGLE_NO_VIEW)) != 0;
            if (widgetPage != null) {
                widgetInAnnots = isWidgetInAnnots(widgetPage, widget);
            }
            PdfDictionary ap = widget.getPdfObject().getAsDictionary(PdfName.AP);
            PdfObject normalAppearance = ap != null ? ap.get(PdfName.N) : null;
            widgetHasAppearance = normalAppearance != null;
        }

        PdfDictionary fieldDict = field.getPdfObject();
        PdfDictionary sigDict = fieldDict != null ? fieldDict.getAsDictionary(PdfName.V) : null;
        if (sigDict == null) {
            throw new IllegalStateException("Field /V is null for signature " + sigName);
        }

        PdfDictionary acroDict = acro.getPdfObject();
        PdfArray acroFieldsArray = acroDict != null ? acroDict.getAsArray(PdfName.Fields) : null;
        PdfArray pageAnnotsArray = widgetPage != null ? widgetPage.getPdfObject().getAsArray(PdfName.Annots) : null;

        Integer acroObjNumber = getObjectNumber(acroDict);
        Integer acroFieldsObjNumber = getObjectNumber(acroFieldsArray);
        Integer fieldObjNumber = getObjectNumber(fieldDict);
        Integer sigObjNumber = getObjectNumber(sigDict);
        Integer widgetObjNumber = widget != null ? getObjectNumber(widget.getPdfObject()) : null;
        Integer pageObjNumber = widgetPage != null ? getObjectNumber(widgetPage.getPdfObject()) : null;
        Integer annotsObjNumber = getObjectNumber(pageAnnotsArray);

        PdfName type = sigDict.getAsName(PdfName.Type);
        if (!PdfName.Sig.equals(type)) {
            String actual = type != null ? "/" + type.getValue() : "null";
            throw new IllegalStateException("Field /V Type not /Sig for " + sigName + ": " + actual);
        }

        PdfName filter = sigDict.getAsName(PdfName.Filter);
        PdfName subFilter = sigDict.getAsName(PdfName.SubFilter);

        List<String> adobeVisibilityIssues = new ArrayList<>();

        boolean filterAllowed = true;
        if (!PdfName.Adobe_PPKLite.equals(filter)) {
            filterAllowed = false;
            adobeVisibilityIssues.add("Filter is not /Adobe.PPKLite");
        }
        if (!ALLOWED_SUBFILTERS.contains(subFilter)) {
            filterAllowed = false;
            adobeVisibilityIssues.add("SubFilter not in CMS/CAdES whitelist");
        }

        PdfArray byteRange = sigDict.getAsArray(PdfName.ByteRange);
        long[] br = null;
        boolean byteRangeNumbersOk = false;
        boolean byteRangeShapeOk = false;
        boolean byteRangeOffsetsOk = false;
        boolean byteRangeCoverageOk = false;
        long fileLength = Files.size(pdfPath);
        if (byteRange == null || byteRange.size() != 4) {
            adobeVisibilityIssues.add("/ByteRange missing or length != 4");
        } else {
            br = new long[4];
            byteRangeNumbersOk = true;
            for (int i = 0; i < 4; i++) {
                PdfNumber num = byteRange.getAsNumber(i);
                if (num == null) {
                    byteRangeNumbersOk = false;
                    adobeVisibilityIssues.add("/ByteRange element " + i + " not a number");
                    break;
                }
                br[i] = num.longValue();
            }
            if (byteRangeNumbersOk) {
                boolean nonNegative = br[1] >= 0 && br[2] >= 0 && br[3] >= 0;
                if (!nonNegative) {
                    byteRangeNumbersOk = false;
                    adobeVisibilityIssues.add("/ByteRange has negative values");
                }
                byteRangeShapeOk = byteRangeNumbersOk && br[0] == 0L;
                if (!byteRangeShapeOk) {
                    adobeVisibilityIssues.add("/ByteRange[0] must be 0");
                }
                byteRangeOffsetsOk = byteRangeNumbersOk && br[2] >= br[1]
                        && br[1] <= fileLength && br[2] + br[3] <= fileLength;
                if (!byteRangeOffsetsOk) {
                    adobeVisibilityIssues.add("/ByteRange offsets overlap or exceed file");
                }
            }
        }

        PdfString contents = sigDict.getAsString(PdfName.Contents);
        boolean contentsHex = false;
        boolean contentsEvenLength = false;
        boolean contentsDecoded = false;
        long contentsHexLength = -1L;
        if (contents == null) {
            adobeVisibilityIssues.add("/Contents missing");
        } else {
            contentsHex = contents.isHexWriting();
            if (!contentsHex) {
                adobeVisibilityIssues.add("/Contents not stored as hex");
            }
            byte[] valueBytes = null;
            try {
                valueBytes = contents.getValueBytes();
                contentsDecoded = true;
            } catch (Exception e) {
                adobeVisibilityIssues.add("/Contents decode failed: " + e.getMessage());
            }
            if (valueBytes != null) {
                if (contentsHex) {
                    contentsHexLength = (long) valueBytes.length * 2L;
                } else {
                    contentsHexLength = valueBytes.length;
                }
                contentsEvenLength = (contentsHexLength & 1L) == 0L;
                if (contentsHex && !contentsEvenLength) {
                    adobeVisibilityIssues.add("/Contents hex length must be even");
                }
            }
        }

        long byteRangeGap = -1L;
        if (byteRangeNumbersOk && br != null) {
            byteRangeGap = br[2] - (br[0] + br[1]);
        }

        if (byteRangeNumbersOk && contents != null && br != null && byteRangeGap >= 0L) {
            boolean coverageLengthMatch = br[1] + br[3] + byteRangeGap == fileLength;
            long expectedGap = contentsDecoded && contentsHexLength >= 0L
                    ? contentsHexLength + 2L
                    : -1L;
            boolean gapMatch = expectedGap >= 0L && byteRangeGap == expectedGap;
            byteRangeCoverageOk = coverageLengthMatch && gapMatch;
            if (!coverageLengthMatch) {
                adobeVisibilityIssues.add("/ByteRange segments + /Contents length != file length");
            }
            if (!gapMatch) {
                adobeVisibilityIssues.add("/ByteRange gap does not match /Contents length");
            }
        }

        boolean adobeVisible = byteRangeShapeOk && byteRangeOffsetsOk && byteRangeCoverageOk
                && contentsHex && contentsEvenLength && filterAllowed;

        boolean pkcs7Parsed = false;
        boolean pkcs7Valid = false;
        String pkcs7Error = null;
        String subject = null;
        try {
            PdfPKCS7 pkcs7 = util.readSignatureData(sigName);
            if (pkcs7 != null) {
                pkcs7Parsed = true;
                try {
                    pkcs7Valid = pkcs7.verifySignatureIntegrityAndAuthenticity();
                } catch (Exception e) {
                    pkcs7Error = "Integrity check error: " + e.getMessage();
                }
                Certificate signingCert = pkcs7.getSigningCertificate();
                if (signingCert == null) {
                    pkcs7Error = pkcs7Error == null
                            ? "Signing certificate missing in PKCS#7"
                            : pkcs7Error + "; signing certificate missing";
                } else {
                    subject = extractSubject(signingCert);
                }
            } else {
                pkcs7Error = "SignatureUtil returned null PdfPKCS7";
            }
        } catch (Exception e) {
            pkcs7Error = e.getMessage();
        }

        return new SignatureCheckResult(
                sigName,
                filter,
                subFilter,
                filterAllowed,
                pkcs7Parsed,
                pkcs7Valid,
                pkcs7Error,
                br,
                byteRangeShapeOk,
                byteRangeOffsetsOk,
                byteRangeCoverageOk,
                contentsHex,
                contentsEvenLength,
                contentsDecoded,
                contentsHexLength,
                adobeVisible,
                adobeVisibilityIssues,
                pageNumber,
                widgetRect != null ? new Rectangle(widgetRect) : null,
                flags,
                widgetPrintable,
                widgetHidden,
                widgetInAnnots,
                widgetHasAppearance,
                subject,
                acroObjNumber,
                acroFieldsObjNumber,
                fieldObjNumber,
                sigObjNumber,
                widgetObjNumber,
                pageObjNumber,
                annotsObjNumber);
    }

    private static PdfWidgetAnnotation selectWidget(List<PdfWidgetAnnotation> widgets) {
        if (widgets == null || widgets.isEmpty()) {
            return null;
        }
        PdfWidgetAnnotation fallback = null;
        for (PdfWidgetAnnotation widget : widgets) {
            if (widget == null) {
                continue;
            }
            if (fallback == null) {
                fallback = widget;
            }
            PdfPage page = widget.getPage();
            if (page != null) {
                return widget;
            }
        }
        return fallback;
    }

    private static boolean isWidgetInAnnots(PdfPage page, PdfWidgetAnnotation widget) {
        if (page == null || widget == null) {
            return false;
        }
        PdfArray annots = page.getPdfObject().getAsArray(PdfName.Annots);
        if (annots == null) {
            return false;
        }
        PdfObject widgetObject = widget.getPdfObject();
        for (int i = 0; i < annots.size(); i++) {
            PdfObject candidate = annots.get(i);
            if (candidate == null) {
                continue;
            }
            if (candidate.getIndirectReference() != null && widgetObject.getIndirectReference() != null
                    && candidate.getIndirectReference().equals(widgetObject.getIndirectReference())) {
                return true;
            }
            if (candidate.equals(widgetObject)) {
                return true;
            }
        }
        return false;
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
        private final boolean filterAllowed;
        private final boolean pkcs7Parsed;
        private final boolean pkcs7Valid;
        private final String pkcs7Error;
        private final long[] byteRange;
        private final boolean byteRangeShapeOk;
        private final boolean byteRangeOffsetsOk;
        private final boolean byteRangeCoverageOk;
        private final boolean contentsHex;
        private final boolean contentsEvenLength;
        private final boolean contentsDecoded;
        private final long contentsHexLength;
        private final boolean adobeVisibleMinimal;
        private final List<String> adobeVisibilityIssues;
        private final int pageNumber;
        private final Rectangle widgetRect;
        private final int widgetFlags;
        private final boolean widgetPrintable;
        private final boolean widgetHidden;
        private final boolean widgetInAnnots;
        private final boolean widgetHasAppearance;
        private final String signingCertificateSubject;
        private final Integer acroFormObjectNumber;
        private final Integer acroFormFieldsObjectNumber;
        private final Integer fieldObjectNumber;
        private final Integer signatureDictionaryObjectNumber;
        private final Integer widgetObjectNumber;
        private final Integer widgetPageObjectNumber;
        private final Integer annotsArrayObjectNumber;

        SignatureCheckResult(String name, PdfName filter, PdfName subFilter, boolean filterAllowed,
                              boolean pkcs7Parsed, boolean pkcs7Valid, String pkcs7Error,
                              long[] byteRange, boolean byteRangeShapeOk, boolean byteRangeOffsetsOk,
                              boolean byteRangeCoverageOk, boolean contentsHex, boolean contentsEvenLength,
                              boolean contentsDecoded, long contentsHexLength, boolean adobeVisibleMinimal,
                              List<String> adobeVisibilityIssues, int pageNumber, Rectangle widgetRect,
                              int widgetFlags, boolean widgetPrintable, boolean widgetHidden,
                              boolean widgetInAnnots, boolean widgetHasAppearance, String signingCertificateSubject,
                              Integer acroFormObjectNumber, Integer acroFormFieldsObjectNumber,
                              Integer fieldObjectNumber, Integer signatureDictionaryObjectNumber,
                              Integer widgetObjectNumber, Integer widgetPageObjectNumber,
                              Integer annotsArrayObjectNumber) {
            this.name = name;
            this.filter = filter;
            this.subFilter = subFilter;
            this.filterAllowed = filterAllowed;
            this.pkcs7Parsed = pkcs7Parsed;
            this.pkcs7Valid = pkcs7Valid;
            this.pkcs7Error = pkcs7Error;
            this.byteRange = byteRange != null ? byteRange.clone() : null;
            this.byteRangeShapeOk = byteRangeShapeOk;
            this.byteRangeOffsetsOk = byteRangeOffsetsOk;
            this.byteRangeCoverageOk = byteRangeCoverageOk;
            this.contentsHex = contentsHex;
            this.contentsEvenLength = contentsEvenLength;
            this.contentsDecoded = contentsDecoded;
            this.contentsHexLength = contentsHexLength;
            this.adobeVisibleMinimal = adobeVisibleMinimal;
            this.adobeVisibilityIssues = Collections.unmodifiableList(new ArrayList<>(adobeVisibilityIssues));
            this.pageNumber = pageNumber;
            this.widgetRect = widgetRect;
            this.widgetFlags = widgetFlags;
            this.widgetPrintable = widgetPrintable;
            this.widgetHidden = widgetHidden;
            this.widgetInAnnots = widgetInAnnots;
            this.widgetHasAppearance = widgetHasAppearance;
            this.signingCertificateSubject = signingCertificateSubject;
            this.acroFormObjectNumber = acroFormObjectNumber;
            this.acroFormFieldsObjectNumber = acroFormFieldsObjectNumber;
            this.fieldObjectNumber = fieldObjectNumber;
            this.signatureDictionaryObjectNumber = signatureDictionaryObjectNumber;
            this.widgetObjectNumber = widgetObjectNumber;
            this.widgetPageObjectNumber = widgetPageObjectNumber;
            this.annotsArrayObjectNumber = annotsArrayObjectNumber;
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

        public boolean isFilterAllowed() {
            return filterAllowed;
        }

        public boolean isPkcs7Parsed() {
            return pkcs7Parsed;
        }

        public boolean isPkcs7Valid() {
            return pkcs7Valid;
        }

        public String getPkcs7Error() {
            return pkcs7Error;
        }

        public long[] getByteRange() {
            return byteRange != null ? byteRange.clone() : null;
        }

        public boolean isByteRangeShapeOk() {
            return byteRangeShapeOk;
        }

        public boolean isByteRangeOffsetsOk() {
            return byteRangeOffsetsOk;
        }

        public boolean isByteRangeCoverageOk() {
            return byteRangeCoverageOk;
        }

        public boolean isContentsHex() {
            return contentsHex;
        }

        public boolean isContentsEvenLength() {
            return contentsEvenLength;
        }

        public boolean isContentsDecoded() {
            return contentsDecoded;
        }

        public long getContentsHexLength() {
            return contentsHexLength;
        }

        public boolean isAdobeVisibleMinimalStructure() {
            return adobeVisibleMinimal;
        }

        public List<String> getAdobeVisibilityIssues() {
            return adobeVisibilityIssues;
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

        public boolean isWidgetPrintable() {
            return widgetPrintable;
        }

        public boolean isWidgetHidden() {
            return widgetHidden;
        }

        public boolean isWidgetInAnnots() {
            return widgetInAnnots;
        }

        public boolean hasWidgetAppearance() {
            return widgetHasAppearance;
        }

        public String getSigningCertificateSubject() {
            return signingCertificateSubject;
        }

        public Integer getAcroFormObjectNumber() {
            return acroFormObjectNumber;
        }

        public Integer getAcroFormFieldsObjectNumber() {
            return acroFormFieldsObjectNumber;
        }

        public Integer getFieldObjectNumber() {
            return fieldObjectNumber;
        }

        public Integer getSignatureDictionaryObjectNumber() {
            return signatureDictionaryObjectNumber;
        }

        public Integer getWidgetObjectNumber() {
            return widgetObjectNumber;
        }

        public Integer getWidgetPageObjectNumber() {
            return widgetPageObjectNumber;
        }

        public Integer getAnnotsArrayObjectNumber() {
            return annotsArrayObjectNumber;
        }

        public String formatByteRange() {
            if (byteRange == null || byteRange.length != 4) {
                return "<invalid>";
            }
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
        return certificate != null ? certificate.toString() : null;
    }

    private static Integer getObjectNumber(PdfObject object) {
        if (object == null || object.getIndirectReference() == null) {
            return null;
        }
        return object.getIndirectReference().getObjNumber();
    }
}
