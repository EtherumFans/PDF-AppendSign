package com.demo.pdf;

import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;

import java.util.Objects;

public final class FieldLockUtil {

    private FieldLockUtil() {
    }

    public static void applyIncludeLock(PdfFormField signatureField, String... fieldNames) {
        Objects.requireNonNull(signatureField, "signatureField");
        PdfDictionary lock = new PdfDictionary();
        lock.put(PdfName.Type, PdfName.SigFieldLock);
        lock.put(PdfName.Action, PdfName.Include);
        PdfArray array = new PdfArray();
        for (String name : fieldNames) {
            if (name != null && !name.isBlank()) {
                array.add(new PdfString(name));
            }
        }
        lock.put(PdfName.Fields, array);
        signatureField.getPdfObject().put(PdfName.Lock, lock);
    }
}
