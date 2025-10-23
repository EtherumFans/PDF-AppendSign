package com.demo.pdf;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;

/**
 * Helper utilities for widget annotations that must live inside a page's /Annots array.
 */
public final class PdfWidgetUtil {

    private PdfWidgetUtil() {
    }

    public static void ensureWidgetInAnnots(PdfPage page, PdfWidgetAnnotation widget, String fieldName) {
        PdfPage actualPage = page != null ? page : widget.getPage();
        if (actualPage == null) {
            throw new IllegalStateException("Signature widget for " + fieldName + " is not associated with any page");
        }
        PdfArray annots = actualPage.getPdfObject().getAsArray(PdfName.Annots);
        if (annots == null) {
            int pageNumber = actualPage.getDocument() != null ? actualPage.getDocument().getPageNumber(actualPage) : -1;
            throw new IllegalStateException("Page " + pageNumber + " has no /Annots array for signature " + fieldName);
        }
        PdfObject widgetObject = widget.getPdfObject();
        boolean found = false;
        for (int i = 0; i < annots.size(); i++) {
            PdfObject candidate = annots.get(i);
            if (candidate == null) {
                continue;
            }
            if (candidate.getIndirectReference() != null && widgetObject.getIndirectReference() != null
                    && candidate.getIndirectReference().equals(widgetObject.getIndirectReference())) {
                found = true;
                break;
            }
            if (candidate.equals(widgetObject)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("Signature widget for " + fieldName
                    + " is not listed inside the page /Annots array");
        }
    }
}
