package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfBoolean;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;

public final class FormUtil {

    private FormUtil() {
    }

    public static PdfAcroForm ensureAcroForm(PdfDocument document) {
        PdfAcroForm form = PdfAcroForm.getAcroForm(document, false);
        if (form == null) {
            form = PdfAcroForm.getAcroForm(document, true);
        }
        if (form.getPdfObject().getAsName(PdfName.DA) == null) {
            form.getPdfObject().put(PdfName.NeedAppearances, PdfBoolean.TRUE);
        }
        return form;
    }
}
