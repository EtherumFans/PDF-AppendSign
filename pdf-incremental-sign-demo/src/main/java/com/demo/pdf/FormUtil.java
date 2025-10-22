package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfBoolean;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;

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
}
