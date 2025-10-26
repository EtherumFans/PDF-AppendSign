package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;

import java.util.List;

public final class FormUtil {

    private static final PdfName HELV_FONT_NAME = new PdfName("Helv");
    private static final PdfName F1_FONT_NAME = new PdfName("F1");
    private static final PdfName ZADB_FONT_NAME = new PdfName("ZaDb");
    public static final String DEFAULT_APPEARANCE = "/Helv 12 Tf 0 g";
    public static final int SIG_FLAG_SIGNATURES_EXIST = 1;
    public static final int SIG_FLAG_APPEND_ONLY = 1 << 1;

    private FormUtil() {
    }

    public static PdfAcroForm prepareAcroForm(PdfDocument document) {
        PdfAcroForm form = PdfAcroForm.getAcroForm(document, true);
        ensureAcroFormAppearanceDefaults(document, form);
        return form;
    }

    public static void ensureAcroFormAppearanceDefaults(PdfDocument document, PdfAcroForm form) {
        if (document == null || form == null) {
            return;
        }

        boolean modified = false;
        if (form.getPdfObject().containsKey(PdfName.NeedAppearances)) {
            form.getPdfObject().remove(PdfName.NeedAppearances);
            modified = true;
        }

        PdfString defaultAppearance = form.getDefaultAppearance();
        String defaultAppearanceValue = defaultAppearance != null ? defaultAppearance.getValue() : null;
        if (defaultAppearanceValue == null || !DEFAULT_APPEARANCE.equals(defaultAppearanceValue)) {
            form.setDefaultAppearance(DEFAULT_APPEARANCE);
            modified = true;
        }

        PdfDictionary dr = form.getPdfObject().getAsDictionary(PdfName.DR);
        if (dr == null) {
            dr = new PdfDictionary();
            form.getPdfObject().put(PdfName.DR, dr);
            modified = true;
        }

        PdfDictionary fonts = dr.getAsDictionary(PdfName.Font);
        if (fonts == null) {
            fonts = new PdfDictionary();
            dr.put(PdfName.Font, fonts);
            modified = true;
        }

        FontRegistration helvetica = ensureFontResource(document, fonts, HELV_FONT_NAME, StandardFonts.HELVETICA);
        FontRegistration zapf = ensureFontResource(document, fonts, ZADB_FONT_NAME, StandardFonts.ZAPFDINGBATS);
        modified = helvetica.changed || modified;
        modified = zapf.changed || modified;
        modified = ensureFontAlias(fonts, HELV_FONT_NAME, helvetica.font) || modified;
        modified = ensureFontAlias(fonts, F1_FONT_NAME, helvetica.font) || modified;
        modified = ensureFontAlias(fonts, ZADB_FONT_NAME, zapf.font) || modified;

        if (modified) {
            form.getPdfObject().setModified();
        }
    }

    public static void setFieldDefaultAppearance(PdfFormField field) {
        if (field == null) {
            return;
        }
        field.getPdfObject().put(PdfName.DA, new PdfString(DEFAULT_APPEARANCE));
        field.getPdfObject().setModified();
    }

    private static FontRegistration ensureFontResource(PdfDocument document,
                                                       PdfDictionary fonts,
                                                       PdfName alias,
                                                       String fontName) {
        try {
            PdfObject existing = fonts.get(alias);
            PdfFont font = PdfFontFactory.createFont(fontName);
            font.makeIndirect(document);
            fonts.put(alias, font.getPdfObject());
            boolean changed = existing == null || !font.getPdfObject().equals(existing);
            return new FontRegistration(font, changed);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to add font resource '" + fontName + "'", e);
        }
    }

    private static boolean ensureFontAlias(PdfDictionary fonts, PdfName alias, PdfFont font) {
        if (fonts == null || alias == null || font == null) {
            return false;
        }
        if (font.getPdfObject().equals(fonts.get(alias))) {
            return false;
        }
        fonts.put(alias, font.getPdfObject());
        return true;
    }

    private static final class FontRegistration {
        private final PdfFont font;
        private final boolean changed;

        private FontRegistration(PdfFont font, boolean changed) {
            this.font = font;
            this.changed = changed;
        }
    }

    public static PdfWidgetAnnotation ensurePrintableSignatureWidget(PdfSignatureFormField field,
                                                                     PdfPage page,
                                                                     Rectangle rect) {
        if (field == null) {
            throw new IllegalArgumentException("Signature field must not be null");
        }
        if (page == null) {
            throw new IllegalArgumentException("Page must not be null when normalizing signature widget");
        }
        if (rect == null) {
            throw new IllegalArgumentException("Rectangle must not be null when normalizing signature widget");
        }

        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            PdfWidgetAnnotation widgetAnnotation = new PdfWidgetAnnotation(rect);
            widgetAnnotation.setPage(page);
            field.addKid(widgetAnnotation);
            widgets = field.getWidgets();
        }

        PdfWidgetAnnotation widget = widgets.get(0);
        PdfDictionary widgetObject = widget.getPdfObject();
        PdfDictionary fieldObject = field.getPdfObject();

        boolean mergedFieldAndWidget = isMergedFieldAndWidget(fieldObject, widgetObject);

        widgetObject.put(PdfName.Subtype, PdfName.Widget);
        widgetObject.put(PdfName.Type, PdfName.Annot);

        float[] rectCoords = new float[]{rect.getLeft(), rect.getBottom(), rect.getRight(), rect.getTop()};
        widget.setRectangle(new PdfArray(rectCoords));
        widget.setPage(page);
        widgetObject.put(PdfName.P, page.getPdfObject());

        if (mergedFieldAndWidget) {
            widgetObject.remove(PdfName.Parent);
            fieldObject.remove(PdfName.Kids);
        } else {
            widgetObject.put(PdfName.Parent, fieldObject);
            PdfArray kids = fieldObject.getAsArray(PdfName.Kids);
            if (kids == null) {
                kids = new PdfArray();
                fieldObject.put(PdfName.Kids, kids);
            }
            if (!containsDictionaryReference(kids, widgetObject)) {
                kids.add(widgetObject);
            }
        }

        PdfArray annots = page.getPdfObject().getAsArray(PdfName.Annots);
        if (annots == null) {
            annots = new PdfArray();
            page.getPdfObject().put(PdfName.Annots, annots);
        }
        if (!containsDictionaryReference(annots, widgetObject)) {
            annots.add(widgetObject);
        }

        int flags = widget.getFlags();
        flags |= PdfAnnotation.PRINT;
        flags &= ~PdfAnnotation.INVISIBLE;
        flags &= ~PdfAnnotation.HIDDEN;
        flags &= ~PdfAnnotation.NO_VIEW;
        flags &= ~PdfAnnotation.TOGGLE_NO_VIEW;
        widget.setFlags(flags);
        widget.setHighlightMode(PdfAnnotation.HIGHLIGHT_NONE);
        return widget;
    }

    public static void ensureWidgetHasAppearance(PdfWidgetAnnotation widget,
                                                 PdfDocument document,
                                                 Rectangle rect) {
        if (widget == null) {
            throw new IllegalArgumentException("Widget must not be null");
        }
        if (document == null) {
            throw new IllegalArgumentException("Document must not be null");
        }
        if (rect == null || rect.getWidth() <= 0 || rect.getHeight() <= 0) {
            throw new IllegalArgumentException("Widget rectangle must be positive when generating appearance");
        }

        PdfDictionary widgetDict = widget.getPdfObject();
        PdfDictionary appearanceDict = widgetDict.getAsDictionary(PdfName.AP);
        if (appearanceDict != null && appearanceDict.containsKey(PdfName.N)) {
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
        canvas.release();

        widget.setAppearance(PdfName.N, appearance.getPdfObject());
        widget.setAppearanceState(PdfName.N);
    }

    public static boolean rectanglesIntersect(Rectangle first, Rectangle second) {
        if (first == null || second == null) {
            return false;
        }
        float firstLeft = Math.min(first.getLeft(), first.getRight());
        float firstRight = Math.max(first.getLeft(), first.getRight());
        float firstBottom = Math.min(first.getBottom(), first.getTop());
        float firstTop = Math.max(first.getBottom(), first.getTop());

        float secondLeft = Math.min(second.getLeft(), second.getRight());
        float secondRight = Math.max(second.getLeft(), second.getRight());
        float secondBottom = Math.min(second.getBottom(), second.getTop());
        float secondTop = Math.max(second.getBottom(), second.getTop());

        boolean horizontalOverlap = firstLeft < secondRight && firstRight > secondLeft;
        boolean verticalOverlap = firstBottom < secondTop && firstTop > secondBottom;
        return horizontalOverlap && verticalOverlap;
    }

    private static boolean isMergedFieldAndWidget(PdfDictionary fieldObject, PdfDictionary widgetObject) {
        if (fieldObject == null || widgetObject == null) {
            return false;
        }
        if (fieldObject == widgetObject) {
            return true;
        }
        if (fieldObject.getIndirectReference() != null && widgetObject.getIndirectReference() != null) {
            return fieldObject.getIndirectReference().equals(widgetObject.getIndirectReference());
        }
        return false;
    }

    private static boolean containsDictionaryReference(PdfArray array, PdfDictionary dictionary) {
        if (array == null || dictionary == null) {
            return false;
        }
        for (int i = 0; i < array.size(); i++) {
            PdfDictionary candidate = array.getAsDictionary(i);
            if (candidate == null) {
                continue;
            }
            if (candidate.getIndirectReference() != null && dictionary.getIndirectReference() != null
                    && candidate.getIndirectReference().equals(dictionary.getIndirectReference())) {
                return true;
            }
            if (candidate.equals(dictionary)) {
                return true;
            }
        }
        return false;
    }
}
