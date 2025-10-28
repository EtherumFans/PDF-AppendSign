package com.demo.pdf;

import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfAnnotation;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfString;
import com.itextpdf.text.pdf.TextField;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PrivateKeySignature;
import com.itextpdf.text.pdf.security.TSAClient;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.Objects;

/**
 * Helper that fills a nursing record row and signs it incrementally.
 */
public final class NursingRecordSigner {

    private static final float ROW_STEP = 120f;
    private static final int TARGET_PAGE = 1;
    private static final float TIME_LEFT = 36f;
    private static final float TIME_RIGHT = 106f;
    private static final float TIME_BOTTOM = 677.92f;
    private static final float TIME_TOP = 705.92f;
    private static final float TEXT_LEFT = 112f;
    private static final float TEXT_RIGHT = 332f;
    private static final float TEXT_BOTTOM = 643.92f;
    private static final float TEXT_TOP = 739.92f;
    private static final float NURSE_LEFT = 338f;
    private static final float NURSE_RIGHT = 428f;
    private static final float NURSE_BOTTOM = 677.92f;
    private static final float NURSE_TOP = 705.92f;
    private static final float SIG_LEFT = 434f;
    private static final float SIG_RIGHT = 554f;
    private static final float SIG_BOTTOM = 637.92f;
    private static final float SIG_TOP = 745.92f;

    /**
     * Parameters for signing a row.
     */
    public static final class SignParams {
        private String source;
        private String destination;
        private int row;
        private String timeValue;
        private String textValue;
        private String nurse;
        private String pkcs12Path;
        private String password;
        private String reason = "Nursing record approval";
        private String location = "Ward";
        private String contact = "nurse@example.com";
        private String tsaUrl;
        private boolean certifyP3;
        private String cjkFontPath;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getTimeValue() {
            return timeValue;
        }

        public void setTimeValue(String timeValue) {
            this.timeValue = timeValue;
        }

        public String getTextValue() {
            return textValue;
        }

        public void setTextValue(String textValue) {
            this.textValue = textValue;
        }

        public String getNurse() {
            return nurse;
        }

        public void setNurse(String nurse) {
            this.nurse = nurse;
        }

        public String getPkcs12Path() {
            return pkcs12Path;
        }

        public void setPkcs12Path(String pkcs12Path) {
            this.pkcs12Path = pkcs12Path;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            if (reason != null && !reason.isBlank()) {
                this.reason = reason;
            }
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            if (location != null && !location.isBlank()) {
                this.location = location;
            }
        }

        public String getContact() {
            return contact;
        }

        public void setContact(String contact) {
            if (contact != null && !contact.isBlank()) {
                this.contact = contact;
            }
        }

        public String getTsaUrl() {
            return tsaUrl;
        }

        public void setTsaUrl(String tsaUrl) {
            this.tsaUrl = tsaUrl;
        }

        public boolean isCertifyP3() {
            return certifyP3;
        }

        public void setCertifyP3(boolean certifyP3) {
            this.certifyP3 = certifyP3;
        }

        public String getCjkFontPath() {
            return cjkFontPath;
        }

        public void setCjkFontPath(String cjkFontPath) {
            this.cjkFontPath = cjkFontPath;
        }
    }

    public NursingRecordSigner() {
    }

    public void signRow(SignParams params) throws Exception {
        Objects.requireNonNull(params, "params");
        if (params.getRow() < 1) {
            throw new IllegalArgumentException("Row index must be >= 1");
        }
        if (params.getSource() == null || params.getDestination() == null) {
            throw new IllegalArgumentException("Source and destination must be provided");
        }
        if (params.getPkcs12Path() == null || params.getPassword() == null) {
            throw new IllegalArgumentException("PKCS12 path and password must be provided");
        }

        RowFieldNames names = RowFieldNames.forRow(params.getRow());
        Rectangle timeRect = rectForTime(names.row());
        Rectangle textRect = rectForText(names.row());
        Rectangle nurseRect = rectForNurse(names.row());
        Rectangle sigRect = rectForSignature(names.row());

        BaseFont baseFont = resolveBaseFont(params.getCjkFontPath());
        System.out.println("[sign-row] Using font for text fields: " + baseFont.getPostscriptFontName());
        Font appearanceFont = new Font(baseFont, 10f);

        PdfReader reader = null;
        FileOutputStream os = null;
        PdfStamper stamper = null;
        boolean preparedForSignature = false;

        try {
            reader = new PdfReader(params.getSource());
            os = new FileOutputStream(params.getDestination());

            Rectangle pageRect = requirePageRectangle(reader, TARGET_PAGE);
            validateRectangle(timeRect, pageRect, names.timeField());
            validateRectangle(textRect, pageRect, names.textField());
            validateRectangle(nurseRect, pageRect, names.nurseField());
            validateRectangle(sigRect, pageRect, names.signatureField());

            stamper = PdfStamper.createSignature(reader, os, '\0', null, true);

            AcroFields acroFields = stamper.getAcroFields();
            acroFields.setGenerateAppearances(true);

            normalizeAcroForm(reader, stamper);

            ensureTextField(stamper, TARGET_PAGE, timeRect, names.timeField(), baseFont, 10f, Element.ALIGN_LEFT);
            ensureTextField(stamper, TARGET_PAGE, textRect, names.textField(), baseFont, 10f, Element.ALIGN_LEFT);
            ensureTextField(stamper, TARGET_PAGE, nurseRect, names.nurseField(), baseFont, 10f, Element.ALIGN_LEFT);

            ensureSigField(stamper, TARGET_PAGE, sigRect, names.signatureField(),
                    new String[]{names.timeField(), names.textField(), names.nurseField()});

            acroFields = stamper.getAcroFields();

            setFieldProperties(acroFields, names.timeField(), baseFont, 10f, Element.ALIGN_LEFT);
            setFieldProperties(acroFields, names.textField(), baseFont, 10f, Element.ALIGN_LEFT);
            setFieldProperties(acroFields, names.nurseField(), baseFont, 10f, Element.ALIGN_LEFT);

            setAndFlatten(acroFields, stamper, names.timeField(), safe(params.getTimeValue()));
            setAndFlatten(acroFields, stamper, names.textField(), safe(params.getTextValue()));
            setAndFlatten(acroFields, stamper, names.nurseField(), safe(params.getNurse()));

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setVisibleSignature(names.signatureField());
            preparedForSignature = true;

            appearance.setReason(params.getReason());
            appearance.setLocation(params.getLocation());
            appearance.setContact(params.getContact());
            appearance.setSignDate(Calendar.getInstance());
            appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION);
            appearance.setLayer2Font(appearanceFont);
            appearance.setLayer2Text(buildLayer2Text(params));
            if (params.isCertifyP3()) {
                appearance.setCertificationLevel(PdfSignatureAppearance.CERTIFIED_FORM_FILLING_AND_ANNOTATIONS);
            } else {
                appearance.setCertificationLevel(PdfSignatureAppearance.NOT_CERTIFIED);
            }

            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] passwordChars = params.getPassword() == null ? new char[0] : params.getPassword().toCharArray();
            try (FileInputStream pkcs12Stream = new FileInputStream(params.getPkcs12Path())) {
                keyStore.load(pkcs12Stream, passwordChars);
            }
            String alias = (String) keyStore.aliases().nextElement();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, passwordChars);
            Certificate[] chain = keyStore.getCertificateChain(alias);

            ExternalDigest digest = new BouncyCastleDigest();
            ExternalSignature signature = new PrivateKeySignature(privateKey, "SHA256",
                    BouncyCastleProvider.PROVIDER_NAME);

            TSAClient tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }

            MakeSignature.signDetached(appearance, digest, signature, chain, null, null, tsaClient, 0,
                    MakeSignature.CryptoStandard.CMS);
        } catch (Exception e) {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore) {
            }
            if (preparedForSignature) {
                try {
                    new File(params.getDestination()).delete();
                } catch (Exception ignore) {
                }
            }
            throw e;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private void normalizeAcroForm(PdfReader reader, PdfStamper stamper) {
        PdfDictionary catalog = reader.getCatalog();
        PdfDictionary acroForm = catalog.getAsDict(PdfName.ACROFORM);
        if (acroForm == null) {
            acroForm = new PdfDictionary();
            catalog.put(PdfName.ACROFORM, acroForm);
            stamper.markUsed(catalog);
        }
        acroForm.remove(PdfName.NEEDAPPEARANCES);
        stamper.markUsed(acroForm);
    }

    private PdfFormField ensureTextField(PdfStamper stamper,
                                         int page,
                                         Rectangle rect,
                                         String name,
                                         BaseFont baseFont,
                                         float size,
                                         int align) throws Exception {
        AcroFields fields = stamper.getAcroFields();
        if (fields.getFieldItem(name) != null) {
            return null;
        }
        TextField tf = new TextField(stamper.getWriter(), rect, name);
        tf.setFont(baseFont);
        tf.setFontSize(size);
        tf.setAlignment(align);
        PdfFormField field = tf.getTextField();
        field.setFlags(PdfAnnotation.FLAGS_PRINT);
        stamper.addAnnotation(field, page);
        return field;
    }

    private PdfFormField ensureSigField(PdfStamper stamper,
                                        int page,
                                        Rectangle rect,
                                        String sigName,
                                        String[] lockFields) {
        AcroFields fields = stamper.getAcroFields();
        if (fields.getFieldItem(sigName) != null) {
            return null;
        }
        PdfFormField signature = PdfFormField.createSignature(stamper.getWriter());
        signature.setFieldName(sigName);
        signature.setWidget(rect, null);
        signature.setFlags(PdfAnnotation.FLAGS_PRINT);
        if (lockFields != null && lockFields.length > 0) {
            PdfDictionary lock = new PdfDictionary();
            lock.put(PdfName.TYPE, PdfName.SIGFIELDLOCK);
            lock.put(PdfName.ACTION, PdfName.INCLUDE);
            PdfArray fieldsArray = new PdfArray();
            for (String field : lockFields) {
                fieldsArray.add(new PdfString(field));
            }
            lock.put(PdfName.FIELDS, fieldsArray);
            signature.put(PdfName.LOCK, lock);
        }
        stamper.addAnnotation(signature, page);
        return signature;
    }

    private void setFieldProperties(AcroFields fields, String name, BaseFont baseFont, float size, int align)
            throws Exception {
        if (fields.getFieldItem(name) == null) {
            return;
        }
        fields.setFieldProperty(name, "textfont", baseFont, null);
        fields.setFieldProperty(name, "textsize", Float.valueOf(size), null);
        fields.setFieldProperty(name, "alignment", align, null);
    }

    private void setAndFlatten(AcroFields af, PdfStamper stamper, String name, String value) throws Exception {
        if (af.getFieldItem(name) == null) {
            throw new IllegalStateException("Field not found: " + name);
        }
        if (!af.setField(name, value)) {
            throw new IllegalStateException("Unable to set field: " + name);
        }
        af.regenerateField(name);
        stamper.partialFormFlattening(name);
    }

    private static String buildLayer2Text(SignParams params) {
        String nurseLine = safe(params.getNurse());
        String timeLine = safe(params.getTimeValue());
        String textLine = safe(params.getTextValue());
        return nurseLine + "\n" + timeLine + "\n" + textLine;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static Rectangle requirePageRectangle(PdfReader reader, int pageNumber) {
        Rectangle pageSize = reader.getPageSize(pageNumber);
        if (pageSize == null) {
            throw new IllegalStateException("Page " + pageNumber + " not found in document");
        }
        return pageSize;
    }

    private BaseFont resolveBaseFont(String cjkFontPath) throws Exception {
        if (cjkFontPath != null && !cjkFontPath.isBlank()) {
            Path path = Paths.get(cjkFontPath);
            if (Files.exists(path)) {
                return BaseFont.createFont(path.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else {
                System.err.println("[sign-row] CJK font not found at " + path + ", falling back to defaults");
            }
        }
        Path bundled = Paths.get("src/main/resources/NotoSansCJKsc-Regular.otf");
        if (Files.exists(bundled)) {
            return BaseFont.createFont(bundled.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        }
        try {
            byte[] resource = readResourceFont();
            if (resource != null) {
                return BaseFont.createFont("NotoSansCJKsc-Regular.otf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                        false, resource, null, false, false);
            }
        } catch (IOException e) {
            System.err.println("[sign-row] Failed to load embedded CJK font, falling back to Helvetica: " + e.getMessage());
        }
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private static byte[] readResourceFont() throws IOException {
        try (InputStream stream = NursingRecordSigner.class.getResourceAsStream("/NotoSansCJKsc-Regular.otf")) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        }
    }

    private static void validateRectangle(Rectangle rect, Rectangle pageRect, String fieldName) {
        if (rect == null) {
            throw new IllegalStateException("Rectangle not computed for field " + fieldName);
        }
        if (!intersects(rect, pageRect)) {
            throw new IllegalStateException("Field '" + fieldName + "' rectangle " + describeRect(rect)
                    + " is outside of page bounds " + describeRect(pageRect));
        }
    }

    private static boolean intersects(Rectangle rect, Rectangle pageRect) {
        float llx = Math.max(rect.getLeft(), pageRect.getLeft());
        float lly = Math.max(rect.getBottom(), pageRect.getBottom());
        float urx = Math.min(rect.getRight(), pageRect.getRight());
        float ury = Math.min(rect.getTop(), pageRect.getTop());
        return llx < urx && lly < ury;
    }

    private static Rectangle rectForTime(int row) {
        return createRowRectangle(TIME_LEFT, TIME_BOTTOM, TIME_RIGHT, TIME_TOP, row);
    }

    private static Rectangle rectForText(int row) {
        return createRowRectangle(TEXT_LEFT, TEXT_BOTTOM, TEXT_RIGHT, TEXT_TOP, row);
    }

    private static Rectangle rectForNurse(int row) {
        return createRowRectangle(NURSE_LEFT, NURSE_BOTTOM, NURSE_RIGHT, NURSE_TOP, row);
    }

    private static Rectangle rectForSignature(int row) {
        return createRowRectangle(SIG_LEFT, SIG_BOTTOM, SIG_RIGHT, SIG_TOP, row);
    }

    private static Rectangle createRowRectangle(float left, float bottom, float right, float top, int row) {
        float offset = (row - 1) * ROW_STEP;
        float translatedBottom = bottom - offset;
        float translatedTop = top - offset;
        if (translatedTop <= translatedBottom) {
            throw new IllegalStateException("Invalid rectangle geometry for row " + row);
        }
        return new Rectangle(left, translatedBottom, right, translatedTop);
    }

    private static String describeRect(Rectangle rect) {
        return String.format("[%.2f, %.2f, %.2f, %.2f]", rect.getLeft(), rect.getBottom(), rect.getRight(), rect.getTop());
    }

    private static final class RowFieldNames {
        private final int row;
        private final String timeField;
        private final String textField;
        private final String nurseField;
        private final String signatureField;

        private RowFieldNames(int row, String timeField, String textField, String nurseField, String signatureField) {
            this.row = row;
            this.timeField = timeField;
            this.textField = textField;
            this.nurseField = nurseField;
            this.signatureField = signatureField;
        }

        static RowFieldNames forRow(int row) {
            return new RowFieldNames(row, timeName(row), textName(row), nurseName(row), signatureName(row));
        }

        static String timeName(int row) {
            return "row" + row + ".time";
        }

        static String textName(int row) {
            return "row" + row + ".text";
        }

        static String nurseName(int row) {
            return "row" + row + ".nurse";
        }

        static String signatureName(int row) {
            return "sig_row_" + row;
        }

        int row() {
            return row;
        }

        String timeField() {
            return timeField;
        }

        String textField() {
            return textField;
        }

        String nurseField() {
            return nurseField;
        }

        String signatureField() {
            return signatureField;
        }
    }
}
