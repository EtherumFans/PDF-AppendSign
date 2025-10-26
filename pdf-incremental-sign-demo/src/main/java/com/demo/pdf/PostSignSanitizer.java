package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.CompressionConstants;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalizes AcroForm and widget structures after signing without touching the signed bytes.
 */
public final class PostSignSanitizer {

    private static final PdfName SUBFILTER_ETSI_CADES_DETACHED = new PdfName("ETSI.CAdES.detached");

    public void sanitize(Path src, Path dest, String fieldName) throws Exception {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(dest, "dest");
        if (fieldName != null && fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        if (!Files.exists(src)) {
            throw new IllegalArgumentException("Source PDF does not exist: " + src);
        }
        Path parent = dest.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (PdfReader reader = new PdfReader(src.toString());
             PdfWriter writer = new PdfWriter(dest.toString(), new WriterProperties()
                     .addXmpMetadata()
                     .setCompressionLevel(CompressionConstants.NO_COMPRESSION));
             PdfDocument pdf = new PdfDocument(reader, writer, new StampingProperties().useAppendMode())) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                throw new IllegalStateException("No AcroForm present in source PDF.");
            }
            normalizeAcroForm(pdf, acro);

            Map<String, PdfFormField> fields = acro.getFormFields();
            if (fields.isEmpty()) {
                throw new IllegalStateException("AcroForm has no fields to sanitize.");
            }
            boolean foundTarget = fieldName == null;
            for (Map.Entry<String, PdfFormField> entry : fields.entrySet()) {
                String name = entry.getKey();
                PdfFormField field = entry.getValue();
                if (field instanceof PdfSignatureFormField) {
                    if (fieldName != null && fieldName.equals(name)) {
                        foundTarget = true;
                    }
                    normalizeSignatureField(pdf, (PdfSignatureFormField) field, name);
                }
            }
            if (!foundTarget) {
                throw new IllegalStateException("Signature field not found: " + fieldName);
            }
        }
    }

    private void normalizeAcroForm(PdfDocument pdf, PdfAcroForm acro) throws IOException {
        PdfDictionary acroDict = acro.getPdfObject();
        if (acroDict.containsKey(PdfName.NeedAppearances)) {
            acroDict.remove(PdfName.NeedAppearances);
            acroDict.setModified();
        }
        PdfNumber sigFlags = acroDict.getAsNumber(PdfName.SigFlags);
        int currentFlags = sigFlags != null ? sigFlags.intValue() : 0;
        int requiredFlags = PdfAcroForm.SIGNATURES_EXIST | PdfAcroForm.APPEND_ONLY;
        if ((currentFlags & requiredFlags) != requiredFlags) {
            acroDict.put(PdfName.SigFlags, new PdfNumber(currentFlags | requiredFlags));
            acroDict.setModified();
        }

        PdfDictionary dr = acroDict.getAsDictionary(PdfName.DR);
        if (dr == null) {
            dr = new PdfDictionary();
            acroDict.put(PdfName.DR, dr);
            acroDict.setModified();
        }
        PdfDictionary fonts = dr.getAsDictionary(PdfName.Font);
        if (fonts == null) {
            fonts = new PdfDictionary();
            dr.put(PdfName.Font, fonts);
            dr.setModified();
        }
        purgeNonFontEntries(fonts);

        FormUtil.ensureAcroFormAppearanceDefaults(pdf, acro);
        acro.setDefaultAppearance(new PdfString(FormUtil.DEFAULT_APPEARANCE));
        acro.getPdfObject().setModified();
    }

    private void purgeNonFontEntries(PdfDictionary fonts) {
        List<PdfName> toRemove = new ArrayList<>();
        for (PdfName name : fonts.keySet()) {
            PdfDictionary entry = fonts.getAsDictionary(name);
            if (entry == null || !PdfName.Font.equals(entry.getAsName(PdfName.Type))) {
                toRemove.add(name);
            }
        }
        for (PdfName name : toRemove) {
            fonts.remove(name);
        }
        if (!toRemove.isEmpty()) {
            fonts.setModified();
        }
    }

    private void normalizeSignatureField(PdfDocument pdf, PdfSignatureFormField field, String fieldName) throws IOException {
        PdfDictionary dict = field.getPdfObject();
        dict.put(PdfName.FT, PdfName.Sig);

        PdfDictionary sigDict = dict.getAsDictionary(PdfName.V);
        if (sigDict != null) {
            ensureSignatureDictionary(pdf, dict, sigDict);
        } else {
            PdfObject raw = dict.get(PdfName.V);
            if (raw instanceof PdfIndirectReference) {
                PdfObject resolved = ((PdfIndirectReference) raw).getRefersTo();
                if (resolved instanceof PdfDictionary) {
                    ensureSignatureDictionary(pdf, dict, (PdfDictionary) resolved);
                }
            }
        }

        normalizeWidgets(pdf, field, fieldName);
    }

    private void ensureSignatureDictionary(PdfDocument pdf, PdfDictionary fieldDict, PdfDictionary sigDict) {
        if (sigDict.getIndirectReference() == null) {
            sigDict.makeIndirect(pdf);
        }
        PdfIndirectReference ref = sigDict.getIndirectReference();
        if (ref != null) {
            fieldDict.put(PdfName.V, ref);
        }
        if (!PdfName.Sig.equals(sigDict.getAsName(PdfName.Type))) {
            sigDict.put(PdfName.Type, PdfName.Sig);
        }
        PdfName filter = sigDict.getAsName(PdfName.Filter);
        if (!PdfName.Adobe_PPKLite.equals(filter)) {
            sigDict.put(PdfName.Filter, PdfName.Adobe_PPKLite);
        }
        PdfName subFilter = sigDict.getAsName(PdfName.SubFilter);
        if (!(PdfName.Adbe_pkcs7_detached.equals(subFilter)
                || new PdfName("adbe.pkcs7.detached").equals(subFilter)
                || SUBFILTER_ETSI_CADES_DETACHED.equals(subFilter))) {
            sigDict.put(PdfName.SubFilter, PdfName.Adbe_pkcs7_detached);
        }
    }

    private void normalizeWidgets(PdfDocument pdf, PdfSignatureFormField field, String fieldName) throws IOException {
        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            PdfPage page = resolvePage(pdf, null);
            Rectangle rect = defaultRect(pdf, page, fieldName);
            PdfWidgetAnnotation widget = new PdfWidgetAnnotation(rect);
            widget.setPage(page);
            field.addKid(widget);
            widgets = field.getWidgets();
        }
        for (PdfWidgetAnnotation widget : widgets) {
            PdfPage page = resolvePage(pdf, widget);
            Rectangle rect = chooseRect(pdf, widget, page, fieldName);
            PdfWidgetAnnotation normalized = FormUtil.ensurePrintableSignatureWidget(field, page, rect);
            Rectangle normalizedRect = normalized.getRectangle().toRectangle();
            ensureAppearanceWithText(normalized, pdf, normalizedRect);
        }
    }

    private PdfPage resolvePage(PdfDocument pdf, PdfWidgetAnnotation widget) {
        if (widget != null && widget.getPage() != null) {
            return widget.getPage();
        }
        if (pdf.getNumberOfPages() < 1) {
            throw new IllegalStateException("PDF has no pages");
        }
        return pdf.getPage(1);
    }

    private Rectangle chooseRect(PdfDocument pdf, PdfWidgetAnnotation widget, PdfPage page, String fieldName) {
        Rectangle rect = widget != null && widget.getRectangle() != null
                ? widget.getRectangle().toRectangle() : null;
        if (rect != null && rect.getWidth() > 0 && rect.getHeight() > 0 && page != null) {
            Rectangle pageRect = page.getPageSize();
            if (pageRect != null && rect.intersects(pageRect)) {
                return rect;
            }
        }
        return defaultRect(pdf, page, fieldName);
    }

    private Rectangle defaultRect(PdfDocument pdf, PdfPage page, String fieldName) {
        int pageNumber = page != null ? pdf.getPageNumber(page) : 1;
        int rowIndex = SignatureDiagnostics.extractRowIndex(fieldName);
        if (rowIndex > 0) {
            return LayoutUtil.sigRectForRow(pdf, pageNumber, rowIndex);
        }
        PdfPage targetPage = page != null ? page : pdf.getPage(pageNumber);
        Rectangle pageRect = targetPage.getPageSize();
        float width = Math.min(LayoutUtil.SIGNATURE_WIDTH, pageRect.getWidth() - 2 * LayoutUtil.MARGIN);
        float height = Math.min(LayoutUtil.FIELD_HEIGHT * 2f, pageRect.getHeight() - 2 * LayoutUtil.MARGIN);
        float x = pageRect.getRight() - LayoutUtil.MARGIN - width;
        float y = pageRect.getBottom() + LayoutUtil.MARGIN;
        return new Rectangle(x, y, width, height);
    }

    private void ensureAppearanceWithText(PdfWidgetAnnotation widget, PdfDocument document, Rectangle rect) throws IOException {
        PdfDictionary ap = widget.getPdfObject().getAsDictionary(PdfName.AP);
        if (ap != null && ap.get(PdfName.N) != null) {
            return;
        }
        PdfFormXObject appearance = new PdfFormXObject(new Rectangle(rect.getWidth(), rect.getHeight()));
        PdfCanvas canvas = new PdfCanvas(appearance, document);
        canvas.saveState();
        canvas.setLineWidth(0.75f);
        canvas.setStrokeColor(ColorConstants.BLACK);
        canvas.rectangle(0, 0, rect.getWidth(), rect.getHeight());
        canvas.stroke();
        canvas.restoreState();

        canvas.beginText();
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        float fontSize = Math.min(12f, rect.getHeight() * 0.6f);
        if (fontSize < 6f) {
            fontSize = Math.min(12f, rect.getHeight());
        }
        canvas.setFontAndSize(font, fontSize);
        canvas.setFillColor(ColorConstants.BLACK);
        String text = "Signed";
        float textWidth = font.getWidth(text, fontSize);
        float textX = Math.max(2f, (rect.getWidth() - textWidth) / 2f);
        float textY = Math.max(2f, (rect.getHeight() - fontSize) / 2f);
        canvas.moveText(textX, textY);
        canvas.showText(text);
        canvas.endText();
        canvas.release();

        widget.setAppearance(PdfName.N, appearance.getPdfObject());
        widget.setAppearanceState(PdfName.N);
    }
}
