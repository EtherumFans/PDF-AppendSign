package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.signatures.SignatureUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PdfStructureDebugger {

    private static final PdfName SUBFILTER_ADBE_PKCS7_DETACHED = new PdfName("adbe.pkcs7.detached");
    private static final PdfName SUBFILTER_ETSI_CADES_DETACHED = new PdfName("ETSI.CAdES.detached");

    private PdfStructureDebugger() {
    }

    public static int inspect(Path pdfPath) throws Exception {
        if (pdfPath == null) {
            throw new IllegalArgumentException("pdfPath must not be null");
        }
        if (!java.nio.file.Files.exists(pdfPath)) {
            System.err.println("File does not exist: " + pdfPath);
            return 2;
        }

        boolean healthy = true;
        String header = PdfSanityUtil.readHeader(pdfPath);
        System.out.println("Header[0:8] = \"" + sanitizeHeader(header) + "\"");
        if (!header.startsWith("%PDF-")) {
            healthy = false;
            System.out.println("!! " + PdfSanityUtil.HEADER_ERROR);
            System.out.println("Hint: Remove BOM/whitespace before %PDF- and retry signing.");
        }

        try (PdfDocument pdf = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                System.out.println("AcroForm: MISSING");
                System.out.println("Hint: Inject an AcroForm and add signature fields to /Fields.");
                return 2;
            }
            Map<String, PdfFormField> fields = acro.getFormFields();
            System.out.println("AcroForm: " + fields.size() + " field(s)");

            SignatureUtil util = new SignatureUtil(pdf);
            List<String> signatureNames = util.getSignatureNames();
            System.out.println("SignatureUtil names: " + signatureNames);

            List<String> sigRows = new ArrayList<>();
            for (String name : fields.keySet()) {
                if (name != null && name.startsWith("sig_row_")) {
                    sigRows.add(name);
                }
            }
            sigRows.sort(Comparator.naturalOrder());
            if (sigRows.isEmpty()) {
                System.out.println("No sig_row_* fields found in AcroForm /Fields.");
                System.out.println("Hint: Ensure your template/injection creates signature fields named sig_row_N.");
                return healthy ? 1 : 2;
            }

            for (String name : sigRows) {
                PdfFormField field = fields.get(name);
                System.out.println("Field " + name + ":");
                healthy &= inspectSignatureField(pdf, name, field);
            }
        }

        return healthy ? 0 : 2;
    }

    private static boolean inspectSignatureField(PdfDocument pdf, String name, PdfFormField field) {
        boolean ok = true;
        PdfName ft = field.getFormType();
        if (ft == null) {
            ft = field.getPdfObject().getAsName(PdfName.FT);
        }
        System.out.println("  /FT = " + (ft != null ? ft.getValue() : "null"));
        if (!PdfName.Sig.equals(ft)) {
            ok = false;
            System.out.println("  !! Field is not /FT /Sig");
            System.out.println("  Hint: Create signature fields via PdfFormField.createSignature and add them to AcroForm.");
        }

        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            ok = false;
            System.out.println("  !! No widget annotation found");
            System.out.println("  Hint: Ensure the signature field has a visible widget on the target page.");
        } else {
            int rowIndex = parseRowIndex(name);
            if (rowIndex <= 0) {
                ok = false;
                System.out.println("  !! Unable to parse row index from field name");
            }
            for (PdfWidgetAnnotation widget : widgets) {
                Rectangle rect = widget.getRectangle().toRectangle();
                PdfNumber flagNumber = widget.getPdfObject().getAsNumber(PdfName.F);
                int flags = flagNumber != null ? flagNumber.intValue() : widget.getFlags();
                int pageNumber = widget.getPage() != null ? pdf.getPageNumber(widget.getPage()) : -1;
                System.out.printf(Locale.ROOT, "  Widget -> page=%d rect=%s flags=0x%X%n", pageNumber, rect, flags);
                if (pageNumber < 1) {
                    ok = false;
                    System.out.println("  !! Widget is not associated with a page");
                } else if (rowIndex > 0) {
                    Rectangle expected = LayoutUtil.sigRectForRow(pdf, pageNumber, rowIndex);
                    if (!rectanglesSimilar(rect, expected)) {
                        ok = false;
                        System.out.println("  !! Widget rectangle does not match expected layout");
                        System.out.println("  Hint: Use LayoutUtil.sigRectForRow when creating the field/appearance.");
                    }
                }
                if ((flags & PdfAnnotation.PRINT) == 0) {
                    ok = false;
                    System.out.println("  !! Widget is not printable (missing PRINT flag)");
                }
                if ((flags & (PdfAnnotation.HIDDEN | PdfAnnotation.INVISIBLE | PdfAnnotation.TOGGLE_NO_VIEW | PdfAnnotation.NO_VIEW)) != 0) {
                    ok = false;
                    System.out.println("  !! Widget is hidden or not viewable");
                    System.out.println("  Hint: Clear HIDDEN/INVISIBLE/TOGGLE_NO_VIEW flags before signing.");
                }
                PdfArray annots = widget.getPage() != null ? widget.getPage().getPdfObject().getAsArray(PdfName.Annots) : null;
                if (annots == null) {
                    ok = false;
                    System.out.println("  !! Widget page is missing /Annots array");
                    System.out.println("  Hint: Add the widget dictionary to page.addAnnotation(...).");
                } else if (!annotsContains(annots, widget.getPdfObject())) {
                    ok = false;
                    System.out.println("  !! Widget dictionary not listed in page /Annots array");
                    System.out.println("  Hint: Call page.addAnnotation(widget) after creating the signature field.");
                }
            }
        }

        PdfDictionary fieldDict = field.getPdfObject();
        if (field instanceof PdfSignatureFormField) {
            PdfSignatureFormField sig = (PdfSignatureFormField) field;
            PdfWidgetAnnotation w = sig.getWidgets().isEmpty() ? null : sig.getWidgets().get(0);
            PdfDictionary parent = w != null ? w.getPdfObject().getAsDictionary(PdfName.Parent) : null;
            System.out.println("  Widget has /Parent->field: " + (parent != null && parent == fieldDict));
        }
        PdfDictionary sigDict = fieldDict.getAsDictionary(PdfName.V);
        if (sigDict == null) {
            ok = false;
            System.out.println("  !! Field has no /V dictionary (signature not bound)");
            System.out.println("  Hint: Call PdfSigner.setFieldName before signDetached.");
            return ok;
        }

        PdfName type = sigDict.getAsName(PdfName.Type);
        System.out.println("  /V Type = " + (type != null ? type.getValue() : "null"));
        if (!PdfName.Sig.equals(type)) {
            ok = false;
            System.out.println("  !! /V dictionary Type is not /Sig");
        }

        PdfName filter = sigDict.getAsName(PdfName.Filter);
        PdfName subFilter = sigDict.getAsName(PdfName.SubFilter);
        System.out.println("  Filter = " + (filter != null ? filter.getValue() : "null"));
        System.out.println("  SubFilter = " + (subFilter != null ? subFilter.getValue() : "null"));
        if (filter == null || !PdfName.Adobe_PPKLite.equals(filter)) {
            ok = false;
            System.out.println("  !! Filter must be /Adobe.PPKLite");
        }
        if (subFilter == null || !(PdfName.Adbe_pkcs7_detached.equals(subFilter)
                || SUBFILTER_ADBE_PKCS7_DETACHED.equals(subFilter)
                || SUBFILTER_ETSI_CADES_DETACHED.equals(subFilter))) {
            ok = false;
            System.out.println("  !! SubFilter must be /adbe.pkcs7.detached or /ETSI.CAdES.detached");
        }

        PdfArray br = sigDict.getAsArray(PdfName.ByteRange);
        System.out.println("  ByteRange = " + br);
        if (br == null || br.size() != 4) {
            ok = false;
            System.out.println("  !! /ByteRange must contain four numbers starting with 0");
        } else if (br.getAsNumber(0).longValue() != 0L) {
            ok = false;
            System.out.println("  !! /ByteRange[0] must be 0");
        } else {
            long br1 = br.getAsNumber(1).longValue();
            long br2 = br.getAsNumber(2).longValue();
            long br3 = br.getAsNumber(3).longValue();
            if (br1 < 0 || br2 < 0 || br3 < 0) {
                ok = false;
                System.out.println("  !! /ByteRange contains negative values");
            }
            if (br2 < br1) {
                ok = false;
                System.out.println("  !! /ByteRange segments overlap (br[2] < br[1])");
            }
        }

        PdfString contents = sigDict.getAsString(PdfName.Contents);
        System.out.println("  Contents length = " + (contents != null ? contents.getValueBytes().length : -1)
                + ", hex = " + (contents != null && contents.isHexWriting()));
        if (contents == null) {
            ok = false;
            System.out.println("  !! /Contents missing");
        } else if (!contents.isHexWriting()) {
            ok = false;
            System.out.println("  !! /Contents must be hex-encoded");
        } else if ((contents.getValueBytes().length & 1) != 0) {
            ok = false;
            System.out.println("  !! /Contents hex string must have an even length");
        }

        return ok;
    }

    private static String sanitizeHeader(String header) {
        if (header.isEmpty()) {
            return "";
        }
        return header.replace("\n", "\\n").replace("\r", "\\r");
    }

    private static boolean rectanglesSimilar(Rectangle a, Rectangle b) {
        final float tolerance = 0.5f;
        return Math.abs(a.getX() - b.getX()) <= tolerance
                && Math.abs(a.getY() - b.getY()) <= tolerance
                && Math.abs(a.getWidth() - b.getWidth()) <= tolerance
                && Math.abs(a.getHeight() - b.getHeight()) <= tolerance;
    }

    private static boolean annotsContains(PdfArray annots, PdfDictionary widgetDict) {
        for (int i = 0; i < annots.size(); i++) {
            PdfObject obj = annots.get(i);
            if (obj != null && obj.getIndirectReference() == widgetDict.getIndirectReference()) {
                return true;
            }
            if (obj != null && obj.equals(widgetDict)) {
                return true;
            }
        }
        return false;
    }

    private static int parseRowIndex(String name) {
        try {
            return Integer.parseInt(name.substring("sig_row_".length()));
        } catch (Exception e) {
            return -1;
        }
    }
}
