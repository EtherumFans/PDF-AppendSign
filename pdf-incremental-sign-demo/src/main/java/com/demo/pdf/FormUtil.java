package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfBoolean;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;

import java.util.List;

public final class FormUtil {

    private FormUtil() {
    }

    public static PdfAcroForm prepareAcroForm(PdfDocument document) {
        PdfAcroForm form = PdfAcroForm.getAcroForm(document, true);
        ensureNeedAppearances(form);
        return form;
    }

    public static void ensureNeedAppearances(PdfAcroForm form) {
        if (form != null && form.getPdfObject().getAsName(PdfName.DA) == null) {
            form.getPdfObject().put(PdfName.NeedAppearances, PdfBoolean.TRUE);
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
            PdfWidgetAnnotation widget = new PdfWidgetAnnotation(rect);
            widget.setPage(page);
            field.addKid(widget);
            widgets = field.getWidgets();
        }

        PdfWidgetAnnotation widget = widgets.get(0);
        float[] rectCoords = new float[]{rect.getLeft(), rect.getBottom(), rect.getRight(), rect.getTop()};
        widget.setRectangle(new PdfArray(rectCoords));
        widget.setPage(page);
        widget.getPdfObject().put(PdfName.P, page.getPdfObject());

        PdfArray annots = page.getPdfObject().getAsArray(PdfName.Annots);
        if (annots == null) {
            annots = new PdfArray();
            page.getPdfObject().put(PdfName.Annots, annots);
        }
        if (!containsAnnotationReference(annots, widget)) {
            annots.add(widget.getPdfObject());
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

    private static boolean containsAnnotationReference(PdfArray annots, PdfWidgetAnnotation widget) {
        if (annots == null || widget == null) {
            return false;
        }
        PdfDictionary widgetObject = widget.getPdfObject();
        for (int i = 0; i < annots.size(); i++) {
            PdfDictionary candidate = annots.getAsDictionary(i);
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
}
