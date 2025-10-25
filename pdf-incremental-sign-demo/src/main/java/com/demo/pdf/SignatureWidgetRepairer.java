package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Repairs legacy signature widgets so Adobe Acrobat will list them in the Signatures pane.
 */
public final class SignatureWidgetRepairer {

    private SignatureWidgetRepairer() {
    }

    public static void repair(Path src, Path dest) throws Exception {
        if (src == null) {
            throw new IllegalArgumentException("Source PDF path must not be null");
        }
        if (dest == null) {
            throw new IllegalArgumentException("Destination PDF path must not be null");
        }
        if (!Files.exists(src)) {
            throw new IllegalArgumentException("Source PDF does not exist: " + src);
        }
        Path parent = dest.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (PdfReader reader = new PdfReader(src.toString());
             PdfWriter writer = new PdfWriter(dest.toString());
             PdfDocument pdf = new PdfDocument(reader, writer, new StampingProperties().useAppendMode())) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                System.out.println("[fix-widgets] No AcroForm present; nothing to repair");
                return;
            }
            FormUtil.ensureAcroFormAppearanceDefaults(pdf, acro);
            Map<String, PdfFormField> fields = acro.getFormFields();
            if (fields.isEmpty()) {
                System.out.println("[fix-widgets] AcroForm has no fields; nothing to repair");
                return;
            }

            for (Map.Entry<String, PdfFormField> entry : fields.entrySet()) {
                PdfFormField field = entry.getValue();
                if (!(field instanceof PdfSignatureFormField)) {
                    continue;
                }
                normalizeSignatureField(pdf, (PdfSignatureFormField) field, entry.getKey());
            }
        }
    }

    private static void normalizeSignatureField(PdfDocument pdf,
                                                PdfSignatureFormField field,
                                                String fieldName) {
        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        PdfWidgetAnnotation widget = widgets != null && !widgets.isEmpty() ? widgets.get(0) : null;

        int pageNumber = resolvePageNumber(pdf, widget);
        if (pageNumber < 1) {
            pageNumber = 1;
        }
        if (pageNumber > pdf.getNumberOfPages()) {
            pageNumber = pdf.getNumberOfPages();
        }
        PdfPage page = pdf.getPage(pageNumber);

        Rectangle rect = widget != null && widget.getRectangle() != null
                ? widget.getRectangle().toRectangle()
                : null;
        if (rect == null || rect.getWidth() <= 0 || rect.getHeight() <= 0) {
            int rowIndex = SignatureDiagnostics.extractRowIndex(fieldName);
            if (rowIndex > 0) {
                rect = LayoutUtil.sigRectForRow(pdf, pageNumber, rowIndex);
            } else {
                rect = defaultSignatureRect(page);
            }
        }

        boolean hadAppearance = widget != null && hasNormalAppearance(widget);
        PdfWidgetAnnotation normalized = FormUtil.ensurePrintableSignatureWidget(field, page, rect);
        Rectangle normalizedRect = normalized.getRectangle().toRectangle();
        if (!hadAppearance || !hasNormalAppearance(normalized)) {
            FormUtil.ensureWidgetHasAppearance(normalized, pdf, normalizedRect);
        }
        System.out.println("[fix-widgets] Normalized signature field " + fieldName
                + " on page " + pageNumber
                + ", rect=" + normalizedRect
                + ", appearanceCreated=" + (!hadAppearance && hasNormalAppearance(normalized)));
    }

    private static boolean hasNormalAppearance(PdfWidgetAnnotation widget) {
        if (widget == null) {
            return false;
        }
        return widget.getPdfObject().getAsDictionary(PdfName.AP) != null
                && widget.getPdfObject().getAsDictionary(PdfName.AP).get(PdfName.N) != null;
    }

    private static int resolvePageNumber(PdfDocument pdf, PdfWidgetAnnotation widget) {
        if (widget != null && widget.getPage() != null) {
            return pdf.getPageNumber(widget.getPage());
        }
        return 1;
    }

    private static Rectangle defaultSignatureRect(PdfPage page) {
        Rectangle pageSize = page.getPageSize();
        float width = LayoutUtil.SIGNATURE_WIDTH;
        float height = LayoutUtil.FIELD_HEIGHT * 2f;
        float x = pageSize.getRight() - LayoutUtil.MARGIN - width;
        float y = pageSize.getBottom() + LayoutUtil.MARGIN;
        return new Rectangle(x, y, width, height);
    }
}
