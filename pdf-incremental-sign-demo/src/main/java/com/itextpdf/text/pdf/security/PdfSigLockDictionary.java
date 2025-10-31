package com.itextpdf.text.pdf.security;

import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfString;

import java.util.Arrays;
import java.util.Objects;

/**
 * Lightweight replacement for the PdfSigLockDictionary utility that ships with
 * iText 5. Some distributions of the library omit the original helper, which
 * causes compilation to fail even though the underlying PDF functionality is
 * still available. This implementation only covers the behaviour required by
 * the demo application: locking a specific set of fields once the signature is
 * applied.
 */
public class PdfSigLockDictionary extends PdfDictionary {

    /**
     * Describes how the set of provided fields should be interpreted by the
     * lock dictionary. Only the values that are required by the demo are
     * implemented; additional options can be added with ease in the future.
     */
    public enum LockPermissions {
        /**
         * Lock exactly the fields supplied in the array that accompanies the
         * dictionary.
         */
        INCLUDE("Include"),
        /**
         * Lock all fields except those supplied in the array.
         */
        EXCLUDE("Exclude"),
        /**
         * Lock every field in the document.
         */
        ALL("All");

        private final PdfName pdfName;

        LockPermissions(String value) {
            this.pdfName = new PdfName(value);
        }

        PdfName getPdfName() {
            return pdfName;
        }
    }

    private static final PdfName TYPE = PdfName.TYPE;
    private static final PdfName SIG_FIELD_LOCK = new PdfName("SigFieldLock");
    private static final PdfName ACTION = PdfName.ACTION;
    private static final PdfName FIELDS = new PdfName("Fields");

    /**
     * Creates a lock dictionary that is compatible with
     * {@link com.itextpdf.text.pdf.PdfSignatureAppearance#setFieldLockDictionary(PdfSigLockDictionary)}.
     *
     * @param permission how the provided field names should be interpreted
     * @param fields     the relevant field names, may be {@code null}
     */
    private final LockPermissions permission;
    private final String[] fields;

    public PdfSigLockDictionary(LockPermissions permission, String[] fields) {
        super();
        this.permission = permission;
        this.fields = normaliseFields(fields);

        put(TYPE, SIG_FIELD_LOCK);
        if (permission != null) {
            put(ACTION, permission.getPdfName());
        }
        if (this.fields.length > 0) {
            PdfArray array = new PdfArray();
            for (String field : this.fields) {
                array.add(new PdfString(field));
            }
            put(FIELDS, array);
        }
    }

    private static String[] normaliseFields(String[] fields) {
        if (fields == null || fields.length == 0) {
            return new String[0];
        }
        return Arrays.stream(fields)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    public LockPermissions getPermission() {
        return permission;
    }

    public String[] getFields() {
        return fields.clone();
    }
}
