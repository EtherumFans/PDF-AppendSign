package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfAnnotation;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.TextField;
import com.itextpdf.text.pdf.PdfString;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PrivateKeySignature;
import com.itextpdf.text.pdf.security.TSAClient;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.Objects;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Simple helper that fills a nursing record row and signs the corresponding signature field.
 */
public final class NursingRecordSigner {

    private NursingRecordSigner() {
    }

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
    }

    public static void signRow(SignParams params) throws Exception {
        Objects.requireNonNull(params, "params");
        if (params.getRow() < 1) {
            throw new IllegalArgumentException("Row index must be >= 1");
        }
        DemoKeystoreUtil.ensureProvider();

        SigningSupport.SigningContext ctx = SigningSupport.resolve(params.getPkcs12Path(), params.getPassword());
        RowFieldNames names = RowFieldNames.forRow(params.getRow());
        String timeValue = safe(params.getTimeValue());
        String textValue = safe(params.getTextValue());
        String nurseValue = safe(params.getNurse());

        byte[] phaseOneBytes;
        PdfReader readerPhaseOne = new PdfReader(params.getSource());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            PdfStamper stamper = new PdfStamper(readerPhaseOne, buffer, '\0', true);
            try {
                ensureAcroFormOnReaderCatalog(readerPhaseOne);
                stamper.markUsed(readerPhaseOne.getCatalog());

                Rectangle pageRect = requirePageRectangle(readerPhaseOne, TARGET_PAGE);
                BaseFont baseFont = resolveBaseFont();
                System.out.println("[sign-row] Using font for text fields: " + baseFont.getPostscriptFontName());

                AcroFields initialFields = stamper.getAcroFields();
                initialFields.setGenerateAppearances(true);

                Rectangle timeRect = rectForTime(names.row());
                Rectangle textRect = rectForText(names.row());
                Rectangle nurseRect = rectForNurse(names.row());
                Rectangle sigRect = rectForSignature(names.row());

                validateRectangle(timeRect, pageRect, names.timeField());
                validateRectangle(textRect, pageRect, names.textField());
                validateRectangle(nurseRect, pageRect, names.nurseField());
                validateRectangle(sigRect, pageRect, names.signatureField());

                if (initialFields.getFieldItem(names.timeField()) == null) {
                    addTextFieldAndValue(stamper, TARGET_PAGE, timeRect, names.timeField(), timeValue, baseFont, false);
                } else {
                    requireSetField(initialFields, initialFields.setField(names.timeField(), timeValue),
                            names.timeField(), names.row(), pageRect, timeRect);
                }

                AcroFields fieldsAfterTime = stamper.getAcroFields();

                if (fieldsAfterTime.getFieldItem(names.textField()) == null) {
                    addTextFieldAndValue(stamper, TARGET_PAGE, textRect, names.textField(), textValue, baseFont, true);
                } else {
                    requireSetField(fieldsAfterTime, fieldsAfterTime.setField(names.textField(), textValue),
                            names.textField(), names.row(), pageRect, textRect);
                }

                AcroFields fieldsAfterText = stamper.getAcroFields();

                if (fieldsAfterText.getFieldItem(names.nurseField()) == null) {
                    addTextFieldAndValue(stamper, TARGET_PAGE, nurseRect, names.nurseField(), nurseValue, baseFont, false);
                } else {
                    requireSetField(fieldsAfterText, fieldsAfterText.setField(names.nurseField(), nurseValue),
                            names.nurseField(), names.row(), pageRect, nurseRect);
                }

                AcroFields fieldsAfterTextInjection = stamper.getAcroFields();
                if (fieldsAfterTextInjection.getFieldItem(names.signatureField()) == null) {
                    addSignatureField(stamper, TARGET_PAGE, sigRect, names.signatureField());
                }

                AcroFields refreshed = stamper.getAcroFields();
                refreshed.setGenerateAppearances(true);

                setFontProperties(refreshed, names.timeField(), baseFont);
                setFontProperties(refreshed, names.textField(), baseFont);
                setFontProperties(refreshed, names.nurseField(), baseFont);

                refreshed.regenerateField(names.timeField());
                refreshed.regenerateField(names.textField());
                refreshed.regenerateField(names.nurseField());
            } finally {
                stamper.close();
            }
        } finally {
            readerPhaseOne.close();
        }
        phaseOneBytes = buffer.toByteArray();

        PdfReader readerPhaseTwo = new PdfReader(new ByteArrayInputStream(phaseOneBytes));
        try (FileOutputStream os = new FileOutputStream(params.getDestination())) {
            PdfStamper stamper = PdfStamper.createSignature(readerPhaseTwo, os, '\0', null, true);

            AcroFields fields = stamper.getAcroFields();
            if (fields.getFieldItem(names.signatureField()) == null) {
                throw new IllegalStateException("Signature field not found in P2: " + names.signatureField());
            }

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setCertificationLevel(PdfSignatureAppearance.NOT_CERTIFIED);
            appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.NAME_AND_DESCRIPTION);
            appearance.setReason(params.getReason());
            appearance.setLocation(params.getLocation());
            appearance.setContact(params.getContact());
            appearance.setSignDate(Calendar.getInstance());
            appearance.setVisibleSignature(names.signatureField());
            String layer2Text = String.format("%s\n%s\n%s", nurseValue, timeValue, textValue);
            appearance.setLayer2Text(layer2Text);

            ExternalDigest digest = new BouncyCastleDigest();
            ExternalSignature signature =
                    new PrivateKeySignature(ctx.privateKey(), "SHA256", BouncyCastleProvider.PROVIDER_NAME);
            Certificate[] chain = ctx.chain();

            TSAClient tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }

            MakeSignature.signDetached(appearance, digest, signature, chain, null, null, tsaClient, 0,
                    MakeSignature.CryptoStandard.CMS);
        } finally {
            readerPhaseTwo.close();
        }
    }

    private static void ensureAcroFormOnReaderCatalog(PdfReader reader) {
        PdfDictionary catalog = reader.getCatalog();
        PdfDictionary acro = catalog.getAsDict(PdfName.ACROFORM);
        if (acro == null) {
            acro = new PdfDictionary();
            acro.put(PdfName.DA, new PdfString("/Helv 12 Tf 0 g"));
            catalog.put(PdfName.ACROFORM, acro);
        }
        acro.remove(PdfName.NEEDAPPEARANCES);
    }

    private static void addTextFieldAndValue(PdfStamper stamper,
                                             int pageNo,
                                             Rectangle rect,
                                             String name,
                                             String value,
                                             BaseFont baseFont,
                                             boolean multiline) throws Exception {
        TextField tf = new TextField(stamper.getWriter(), rect, name);
        tf.setFont(baseFont);
        tf.setFontSize(12f);
        int options = TextField.READ_ONLY;
        if (multiline) {
            options |= TextField.MULTILINE;
        }
        tf.setOptions(options);
        PdfFormField field = tf.getTextField();
        field.setFlags(PdfAnnotation.FLAGS_PRINT);
        field.setValueAsString(value);
        stamper.addAnnotation(field, pageNo);
        System.out.println("[sign-row] Injected text field '" + name + "' at " + describeRect(rect));
    }

    private static void addSignatureField(PdfStamper stamper,
                                          int pageNo,
                                          Rectangle rect,
                                          String name) {
        PdfFormField signature = PdfFormField.createSignature(stamper.getWriter());
        signature.setFieldName(name);
        signature.setWidget(rect, null);
        signature.setFlags(PdfAnnotation.FLAGS_PRINT);
        stamper.addAnnotation(signature, pageNo);
        System.out.println("[sign-row] Injected signature field '" + name + "' at " + describeRect(rect));
    }

    private static String safe(String value) {
        return value == null ? "" : value.stripTrailing();
    }

    private static void requireSetField(AcroFields fields,
                                        boolean success,
                                        String fieldName,
                                        int row,
                                        Rectangle pageRect,
                                        Rectangle rect) {
        if (success) {
            return;
        }
        boolean present = fields.getFieldItem(fieldName) != null;
        System.err.println("[sign-row] Unable to populate field: " + fieldName);
        System.err.println("[sign-row]   present=" + present + ", rect=" + describeRect(rect));
        System.err.println("[sign-row]   page bounds=" + describeRect(pageRect));
        throw new IllegalStateException("Failed to populate field '" + fieldName + "' for row " + row);
    }

    private static void setFontProperties(AcroFields fields, String name, BaseFont baseFont) throws Exception {
        fields.setFieldProperty(name, "textfont", baseFont, null);
        fields.setFieldProperty(name, "textsize", Float.valueOf(12f), null);
    }

    private static Rectangle requirePageRectangle(PdfReader reader, int pageNumber) {
        Rectangle pageSize = reader.getPageSize(pageNumber);
        if (pageSize == null) {
            throw new IllegalStateException("Page " + pageNumber + " not found in document");
        }
        return pageSize;
    }

    private static BaseFont resolveBaseFont() throws Exception {
        Path cjkPath = Paths.get("src/main/resources/NotoSansCJKsc-Regular.otf");
        if (Files.exists(cjkPath)) {
            return BaseFont.createFont(cjkPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        }
        try {
            byte[] resource = readResourceFont();
            if (resource != null) {
                return BaseFont.createFont("NotoSansCJKsc-Regular.otf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, resource,
                        null, false, false);
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
