package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.signatures.SignatureUtil;

import com.itextpdf.kernel.geom.Rectangle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SignatureVerifier {

    public static int verify(String path) throws Exception {
        return runVerification(path, false, false);
    }

    public static int deepVerify(String path) throws Exception {
        return runVerification(path, true, false);
    }

    private static int runVerification(String path, boolean deepMode, boolean strictTail) throws Exception {
        DemoKeystoreUtil.ensureProvider();
        Path pdfPath = Path.of(path);
        PdfSanityUtil.requireHeader(pdfPath);
        PostSignValidator.TailInfo tailInfo = PostSignValidator.strictTailCheck(pdfPath, strictTail);
        if (tailInfo.getType() != null) {
            System.out.printf("[tail] Using %s at offset %d (declared %d)%n",
                    tailInfo.getType(), tailInfo.getActualOffset(), tailInfo.getDeclaredOffset());
        }
        try (PdfDocument pdf = new PdfDocument(new PdfReader(path))) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                throw new IllegalStateException("No AcroForm present in PDF: " + path);
            }
            SignatureUtil su = new SignatureUtil(pdf);

            List<String> names = su.getSignatureNames();
            System.out.println("PDF: " + path);
            System.out.println("AcroForm signatures: " + names.size() + " -> " + names);

            if (names.isEmpty()) {
                throw new IllegalStateException("No field-bound signatures found (Adobe panel will be empty).");
            }

            Set<Integer> reachable = buildLatestReachableSet(pdf);
            for (String name : names) {
                SignatureDiagnostics.SignatureCheckResult result =
                        SignatureDiagnostics.inspectSignature(pdfPath, pdf, su, acro, name);
                ReachabilityResult reachability = evaluateReachability(result, reachable);

                int rowIndex = SignatureDiagnostics.extractRowIndex(name);
                if (rowIndex > 0) {
                    Rectangle expectedRect = LayoutUtil.sigRectForRow(pdf, result.getPageNumber(), rowIndex);
                    if (result.getWidgetRect() != null
                            && !SignatureDiagnostics.rectanglesSimilar(result.getWidgetRect(), expectedRect)) {
                        throw new IllegalStateException("Signature widget rectangle mismatch for " + name);
                    }
                }

                String subject = result.getSigningCertificateSubject() != null
                        ? result.getSigningCertificateSubject()
                        : "<missing>";
                String filterValue = result.getFilter() != null ? result.getFilter().getValue() : "<null>";
                String subFilterValue = result.getSubFilter() != null ? result.getSubFilter().getValue() : "<null>";
                System.out.printf(" - %s | Filter=/%s, SubFilter=/%s%n",
                        name,
                        filterValue,
                        subFilterValue);
                boolean brShapeOk = result.isByteRangeShapeOk() && result.isByteRangeOffsetsOk();
                boolean contHexOk = result.isContentsHex() && result.isContentsEvenLength();
                System.out.printf("     Adobe-visible(minimal-structure): %s (brShape=%s, contHex=%s, coverage=%s, filter=%s)%n",
                        result.isAdobeVisibleMinimalStructure(),
                        brShapeOk,
                        contHexOk,
                        result.isByteRangeCoverageOk(),
                        result.isFilterAllowed());
                System.out.println("     ADOBE_VISIBLE_MINIMAL: " + result.isAdobeVisibleMinimalStructure());
                if (!result.isAdobeVisibleMinimalStructure()) {
                    for (String reason : result.getAdobeVisibilityIssues()) {
                        System.out.println("       - " + reason);
                    }
                }
                System.out.printf("     PKCS7 parsed=%s, Valid=%s%n", result.isPkcs7Parsed(), result.isPkcs7Valid());
                if (result.getPkcs7Error() != null) {
                    System.out.println("       PKCS7 note: " + result.getPkcs7Error());
                }
                System.out.println("     Signing cert subject: " + subject);
                System.out.println("     ByteRange=" + result.formatByteRange()
                        + ", ContentsHexLen=" + result.getContentsHexLength());
                System.out.println("     Widget PRINT=" + result.isWidgetPrintable()
                        + ", INVISIBLE/HIDDEN/NOVIEW=" + result.isWidgetHidden());
                System.out.println("     Widget page=" + result.getPageNumber()
                        + ", rect=" + result.getWidgetRect()
                        + ", AP(N)=" + result.hasWidgetAppearance()
                        + ", inAnnots=" + result.isWidgetInAnnots());
                System.out.println("     ADOBE_REACHABLE: " + reachability.isReachable());
                if (!reachability.isReachable()) {
                    System.out.println("       Missing in latest revision xref: "
                            + String.join(", ", reachability.getMissingDescriptions()));
                    System.out.println("     Acrobat will not show this signature: objects not reachable in the latest revision ("
                            + String.join("/", reachability.getMissingLabels()) + ")");
                }
                if (deepMode) {
                    boolean minimal = result.isAdobeVisibleMinimalStructure();
                    boolean reachableVerdict = reachability.isReachable();
                    String verdict;
                    if (minimal && reachableVerdict) {
                        verdict = "WILL be shown by Acrobat";
                    } else if (!reachableVerdict) {
                        verdict = "WILL NOT be shown by Acrobat (objects unreachable)";
                    } else {
                        verdict = "WILL NOT be shown by Acrobat (minimal structure failure)";
                    }
                    System.out.println("     => This signature " + verdict
                            + " (based on structure and reachability checks)");
                }
            }
            return 0;
        }
    }

    private static void dumpCatalogAndAcroForm(String pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            PdfDictionary root = doc.getCatalog().getPdfObject();
            if (root == null) {
                System.out.println("Catalog root object missing");
                return;
            }
            if (root.getIndirectReference() != null) {
                System.out.println("Root obj#: " + root.getIndirectReference().getObjNumber());
            } else {
                System.out.println("Root obj#: <direct>");
            }
            PdfDictionary acro = root.getAsDictionary(PdfName.AcroForm);
            if (acro == null) {
                System.out.println("Catalog has NO /AcroForm");
                return;
            }
            if (acro.getIndirectReference() != null) {
                System.out.println("AcroForm obj#: " + acro.getIndirectReference().getObjNumber());
            } else {
                System.out.println("AcroForm obj#: <direct>");
            }
            PdfArray fields = acro.getAsArray(PdfName.Fields);
            System.out.println("AcroForm.Fields count: " + (fields == null ? 0 : fields.size()));
            if (fields != null) {
                for (int i = 0; i < fields.size(); i++) {
                    PdfDictionary f = fields.getAsDictionary(i);
                    if (f == null) {
                        System.out.printf("  [%d] <non-dictionary field entry>%n", i);
                        continue;
                    }
                    PdfName ft = f.getAsName(PdfName.FT);
                    PdfString t = f.getAsString(PdfName.T);
                    if (f.getIndirectReference() != null) {
                        System.out.printf("  [%d] FT=%s, T=%s, obj#=%d%n", i, ft, t,
                                f.getIndirectReference().getObjNumber());
                    } else {
                        System.out.printf("  [%d] FT=%s, T=%s, obj#=<direct>%n", i, ft, t);
                    }
                    PdfObject v = f.get(PdfName.V);
                    if (v != null && v.isDictionary()) {
                        PdfDictionary sig = (PdfDictionary) v;
                        System.out.printf("      /V Type=%s, Filter=%s, SubFilter=%s%n",
                                sig.getAsName(PdfName.Type),
                                sig.getAsName(PdfName.Filter),
                                sig.getAsName(PdfName.SubFilter));
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        boolean strictTail = false;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if ("--strict-tail".equals(arg)) {
                strictTail = true;
            } else {
                positional.add(arg);
            }
        }

        if (positional.isEmpty()) {
            System.err.println("Usage:");
            System.err.println("  java ... SignatureVerifier [--strict-tail] <pdf>");
            System.err.println("  java ... SignatureVerifier [--strict-tail] dump-acroform <pdf>");
            System.err.println("  java ... SignatureVerifier [--strict-tail] deep-verify <pdf>");
            System.exit(2);
        }

        if (positional.size() == 2 && "dump-acroform".equals(positional.get(0))) {
            dumpCatalogAndAcroForm(positional.get(1));
            return;
        }

        if (positional.size() == 2 && "deep-verify".equals(positional.get(0))) {
            int code = runVerification(positional.get(1), true, strictTail);
            System.exit(code);
        }

        if (positional.size() != 1) {
            System.err.println("Unexpected arguments: " + positional);
            System.exit(2);
        }

        int code = runVerification(positional.get(0), false, strictTail);
        System.exit(code);
    }

    public static Set<Integer> buildLatestReachableSet(PdfDocument pdf) {
        Set<Integer> reachable = new HashSet<>();
        int objectCount = pdf.getNumberOfPdfObjects();
        for (int i = 1; i <= objectCount; i++) {
            PdfObject obj = pdf.getPdfObject(i);
            if (obj == null) {
                continue;
            }
            PdfIndirectReference ref = obj.getIndirectReference();
            if (ref != null && !ref.isFree()) {
                reachable.add(ref.getObjNumber());
            }
        }
        return reachable;
    }

    public static ReachabilityResult evaluateReachability(SignatureDiagnostics.SignatureCheckResult result,
                                                           Set<Integer> reachable) {
        List<String> missingDescriptions = new ArrayList<>();
        List<String> missingLabels = new ArrayList<>();
        collectMissing(result.getAcroFormObjectNumber(), "acroform", reachable, missingDescriptions, missingLabels);
        collectMissing(result.getAcroFormFieldsObjectNumber(), "acroformFields", reachable, missingDescriptions, missingLabels);
        collectMissing(result.getFieldObjectNumber(), "field", reachable, missingDescriptions, missingLabels);
        collectMissing(result.getSignatureDictionaryObjectNumber(), "signatureV", reachable, missingDescriptions, missingLabels);
        collectMissing(result.getWidgetObjectNumber(), "widget", reachable, missingDescriptions, missingLabels);
        collectMissing(result.getWidgetPageObjectNumber(), "page", reachable, missingDescriptions, missingLabels);
        collectMissing(result.getAnnotsArrayObjectNumber(), "annots", reachable, missingDescriptions, missingLabels);
        return new ReachabilityResult(missingDescriptions.isEmpty(), missingDescriptions, missingLabels);
    }

    private static void collectMissing(Integer objNumber, String label, Set<Integer> reachable,
                                       List<String> missingDescriptions, List<String> missingLabels) {
        if (objNumber == null) {
            return;
        }
        if (!reachable.contains(objNumber)) {
            missingDescriptions.add(label + " obj#=" + objNumber);
            missingLabels.add(label);
        }
    }

    public static final class ReachabilityResult {
        private final boolean reachable;
        private final List<String> missingDescriptions;
        private final List<String> missingLabels;

        ReachabilityResult(boolean reachable, List<String> missingDescriptions, List<String> missingLabels) {
            this.reachable = reachable;
            this.missingDescriptions = List.copyOf(missingDescriptions);
            this.missingLabels = List.copyOf(missingLabels);
        }

        public boolean isReachable() {
            return reachable;
        }

        public List<String> getMissingDescriptions() {
            return missingDescriptions;
        }

        public List<String> getMissingLabels() {
            return missingLabels;
        }
    }
}
