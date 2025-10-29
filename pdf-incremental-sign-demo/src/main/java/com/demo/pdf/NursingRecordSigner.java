package com.demo.pdf;

import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfContentByte;

/**
 * Helper that fills a nursing record row and signs it incrementally.
 */
public final class NursingRecordSigner {

    private static final float ROW_STEP = 120f;
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
        private boolean fallbackDraw;
        private int pageIndex = 1;
        private float tableTopY = 650f;
        private float rowHeight = 22f;
        private float timeX = 90f;
        private float textX = 150f;
        private float nurseX = 500f;
        private String fontPath;
        private float fontSize = 10f;
        private float textMaxWidth = 330f;
        private boolean signVisible = true;
        private String signFieldTemplate = "sig_row_{row}";
        private float signX = -1f;
        private float signWidth = 120f;
        private float signHeight = 18f;
        private float signYOffset = -12f;

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

        public boolean isFallbackDraw() {
            return fallbackDraw;
        }

        public void setFallbackDraw(boolean fallbackDraw) {
            this.fallbackDraw = fallbackDraw;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public void setPageIndex(int pageIndex) {
            this.pageIndex = pageIndex;
        }

        public float getTableTopY() {
            return tableTopY;
        }

        public void setTableTopY(float tableTopY) {
            this.tableTopY = tableTopY;
        }

        public float getRowHeight() {
            return rowHeight;
        }

        public void setRowHeight(float rowHeight) {
            this.rowHeight = rowHeight;
        }

        public float getTimeX() {
            return timeX;
        }

        public void setTimeX(float timeX) {
            this.timeX = timeX;
        }

        public float getTextX() {
            return textX;
        }

        public void setTextX(float textX) {
            this.textX = textX;
        }

        public float getNurseX() {
            return nurseX;
        }

        public void setNurseX(float nurseX) {
            this.nurseX = nurseX;
        }

        public String getFontPath() {
            return fontPath;
        }

        public void setFontPath(String fontPath) {
            this.fontPath = fontPath;
        }

        public float getFontSize() {
            return fontSize;
        }

        public void setFontSize(float fontSize) {
            this.fontSize = fontSize;
        }

        public float getTextMaxWidth() {
            return textMaxWidth;
        }

        public void setTextMaxWidth(float textMaxWidth) {
            this.textMaxWidth = textMaxWidth;
        }

        public boolean isSignVisible() {
            return signVisible;
        }

        public void setSignVisible(boolean signVisible) {
            this.signVisible = signVisible;
        }

        public String getSignFieldTemplate() {
            return signFieldTemplate;
        }

        public void setSignFieldTemplate(String signFieldTemplate) {
            this.signFieldTemplate = signFieldTemplate;
        }

        public float getSignX() {
            return signX;
        }

        public void setSignX(float signX) {
            this.signX = signX;
        }

        public float getSignWidth() {
            return signWidth;
        }

        public void setSignWidth(float signWidth) {
            this.signWidth = signWidth;
        }

        public float getSignHeight() {
            return signHeight;
        }

        public void setSignHeight(float signHeight) {
            this.signHeight = signHeight;
        }

        public float getSignYOffset() {
            return signYOffset;
        }

        public void setSignYOffset(float signYOffset) {
            this.signYOffset = signYOffset;
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

        int row = params.getRow();
        Rectangle timeRect = rectForTime(row);
        Rectangle textRect = rectForText(row);
        Rectangle nurseRect = rectForNurse(row);
        int pageIndex = params.getPageIndex();
        float tableTopY = params.getTableTopY();
        float rowHeight = params.getRowHeight();
        float nurseX = params.getNurseX();
        float yBase = tableTopY - (row - 1) * rowHeight;
        boolean fallbackActive = shouldFallbackToDrawing(params);
        String sourceForSigning = params.getSource();
        if (fallbackActive) {
            sourceForSigning = applyFallbackDrawing(params);
        }

        BaseFont cjkFont = resolveBaseFont(params.getCjkFontPath());
        System.out.println("[sign-row] Using font for text fields: " + cjkFont.getPostscriptFontName());
        Font appearanceFont = new Font(cjkFont, 10f);

        PdfReader reader = null;
        FileOutputStream os = null;
        PdfStamper stamper = null;
        boolean signDetachedCalled = false;

        try {
            reader = new PdfReader(sourceForSigning);
            os = new FileOutputStream(params.getDestination());

            Rectangle pageRect = requirePageRectangle(reader, pageIndex);
            validateRectangle(timeRect, pageRect, "time");
            validateRectangle(textRect, pageRect, "text");
            validateRectangle(nurseRect, pageRect, "nurse");
            stamper = PdfStamper.createSignature(reader, os, '\0', null, true);

            AcroFields acroFields = stamper.getAcroFields();
            ensureAcroFormIText5(reader, stamper, cjkFont);
            acroFields.addSubstitutionFont(cjkFont);
            acroFields.setGenerateAppearances(true);

            if (!fallbackActive) {
                FieldResolution timeField = resolveOrInjectTextField(
                        stamper,
                        acroFields,
                        row,
                        new String[]{"row%d.time", "recordTime_%d"},
                        timeRect,
                        pageIndex,
                        cjkFont,
                        12f
                );
                if (timeField.created) {
                    acroFields = stamper.getAcroFields();
                }
                FieldResolution textField = resolveOrInjectTextField(
                        stamper,
                        acroFields,
                        row,
                        new String[]{"row%d.text", "recordText_%d"},
                        textRect,
                        pageIndex,
                        cjkFont,
                        12f
                );
                if (textField.created) {
                    acroFields = stamper.getAcroFields();
                }
                FieldResolution nurseField = resolveOrInjectTextField(
                        stamper,
                        acroFields,
                        row,
                        new String[]{"row%d.nurse", "recordNurse_%d"},
                        nurseRect,
                        pageIndex,
                        cjkFont,
                        12f
                );
                if (nurseField.created) {
                    acroFields = stamper.getAcroFields();
                }

                acroFields.setFieldProperty(timeField.name, "textfont", cjkFont, null);
                acroFields.setFieldProperty(textField.name, "textfont", cjkFont, null);
                acroFields.setFieldProperty(nurseField.name, "textfont", cjkFont, null);
                acroFields.setFieldProperty(timeField.name, "textsize", 12f, null);
                acroFields.setFieldProperty(textField.name, "textsize", 12f, null);
                acroFields.setFieldProperty(nurseField.name, "textsize", 12f, null);

                if (!acroFields.setField(timeField.name, safe(params.getTimeValue()))) {
                    throw new IllegalStateException("Unable to set field: " + timeField.name
                            + " fields=" + dumpFieldNames(acroFields));
                }
                if (!acroFields.setField(textField.name, safe(params.getTextValue()))) {
                    throw new IllegalStateException("Unable to set field: " + textField.name
                            + " fields=" + dumpFieldNames(acroFields));
                }
                if (!acroFields.setField(nurseField.name, safe(params.getNurse()))) {
                    throw new IllegalStateException("Unable to set field: " + nurseField.name
                            + " fields=" + dumpFieldNames(acroFields));
                }

                acroFields.setFieldProperty(timeField.name, "setfflags", PdfFormField.FF_READ_ONLY, null);
                acroFields.setFieldProperty(textField.name, "setfflags", PdfFormField.FF_READ_ONLY, null);
                acroFields.setFieldProperty(nurseField.name, "setfflags", PdfFormField.FF_READ_ONLY, null);
                acroFields.regenerateField(timeField.name);
                acroFields.regenerateField(textField.name);
                acroFields.regenerateField(nurseField.name);
            }

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            String signFieldName = "sig_row_" + row;
            AcroFields af = reader.getAcroFields();
            boolean hasField = af != null && af.getFieldItem(signFieldName) != null;

            if (params.isSignVisible()) {
                if (hasField) {
                    appearance.setVisibleSignature(signFieldName);
                    System.out.println("[visible-sign:field] " + signFieldName);
                } else {
                    float x = nurseX;
                    float yBottom = yBase - 12f;
                    float width = 120f;
                    float height = 18f;
                    Rectangle rect = new Rectangle(x, yBottom, x + width, yBottom + height);
                    appearance.setVisibleSignature(rect, pageIndex, signFieldName);
                    System.out.printf("[visible-sign:rect] page=%d rect=[%.1f,%.1f,%.1f,%.1f] field=%s%n",
                            pageIndex, x, yBottom, x + width, yBottom + height, signFieldName);
                }
            } else {
                System.out.println("[visible-sign:none] Invisible signature requested");
            }

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

            signDetachedCalled = true;
            MakeSignature.signDetached(appearance, digest, signature, chain, null, null, tsaClient, 0,
                    MakeSignature.CryptoStandard.CMS);
        } catch (Exception e) {
            try {
                if (!signDetachedCalled && stamper != null) {
                    stamper.close();
                }
            } catch (Exception ignore) {
            }
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
            if (!signDetachedCalled) {
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

    private static String dumpFieldNames(AcroFields af) {
        return String.valueOf(af.getFields().keySet());
    }

    private boolean shouldFallbackToDrawing(SignParams params) throws IOException {
        if (!params.isFallbackDraw()) {
            return false;
        }
        PdfReader reader = null;
        try {
            reader = new PdfReader(params.getSource());
            AcroFields form = reader.getAcroFields();
            if (form == null || form.getFields().isEmpty()) {
                return true;
            }
            int row = params.getRow();
            boolean timeExists = hasAnyField(form,
                    String.format("row%d.time", row),
                    String.format("recordTime_%d", row));
            boolean textExists = hasAnyField(form,
                    String.format("row%d.text", row),
                    String.format("recordText_%d", row));
            boolean nurseExists = hasAnyField(form,
                    String.format("row%d.nurse", row),
                    String.format("recordNurse_%d", row));
            return !(timeExists && textExists && nurseExists);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static boolean hasAnyField(AcroFields form, String... names) {
        if (form == null) {
            return false;
        }
        for (String name : names) {
            if (name != null && form.getField(name) != null) {
                return true;
            }
        }
        return false;
    }

    private String applyFallbackDrawing(SignParams params) throws IOException {
        Path temp = Files.createTempFile("nursing-fallback-row", ".pdf");
        temp.toFile().deleteOnExit();
        PdfReader reader = null;
        FileOutputStream fos = null;
        try {
            reader = new PdfReader(params.getSource());
            fos = new FileOutputStream(temp.toFile());
            PdfStamper stamper = null;
            try {
                stamper = new PdfStamper(reader, fos, '\0', true);
                BaseFont font;
                try {
                    font = resolveFallbackBaseFont(params);
                } catch (Exception e) {
                    throw new IOException("Unable to resolve fallback font", e);
                }
                drawRowFallback(
                        stamper,
                        params.getPageIndex(),
                        params.getRow(),
                        params.getTableTopY(),
                        params.getRowHeight(),
                        params.getTimeX(),
                        safe(params.getTimeValue()),
                        params.getTextX(),
                        safe(params.getTextValue()),
                        params.getNurseX(),
                        safe(params.getNurse()),
                        font,
                        params.getFontSize(),
                        params.getTextMaxWidth()
                );
            } catch (DocumentException e) {
                throw new IOException("Failed to apply fallback drawing", e);
            } finally {
                if (stamper != null) {
                    try {
                        stamper.close();
                    } catch (DocumentException e) {
                        throw new IOException("Failed to finalize fallback drawing", e);
                    }
                }
            }
        } finally {
            if (fos != null) {
                fos.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
        return temp.toAbsolutePath().toString();
    }

    private BaseFont resolveFallbackBaseFont(SignParams params) throws Exception {
        String directFont = params.getFontPath();
        if (directFont != null && !directFont.isBlank()) {
            Path path = Paths.get(directFont);
            if (Files.exists(path)) {
                return BaseFont.createFont(path.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else {
                System.err.println("[sign-row] Fallback font not found at " + path + ", trying defaults");
            }
        }
        return resolveBaseFont(params.getCjkFontPath());
    }

    private static void drawRowFallback(
            PdfStamper stamper,
            int pageIndex1Based,
            int row,
            float tableTopY,
            float rowHeight,
            float timeX, String time,
            float textX, String text,
            float nurseX, String nurse,
            BaseFont font, float fontSize,
            float textMaxWidth
    ) throws IOException {
        int numberOfPages = stamper.getReader().getNumberOfPages();
        if (pageIndex1Based < 1 || pageIndex1Based > numberOfPages) {
            throw new IllegalArgumentException("Page index " + pageIndex1Based + " out of bounds (1-"
                    + numberOfPages + ")");
        }
        if (row < 1) {
            throw new IllegalArgumentException("Row index must be >= 1");
        }
        float y = tableTopY - (row - 1) * rowHeight;
        float lineHeight = fontSize * 1.2f;
        List<String> wrappedText = wrapText(text, font, fontSize, textMaxWidth);
        PdfContentByte canvas = stamper.getOverContent(pageIndex1Based);
        if (time != null && !time.isEmpty()) {
            showTextLine(canvas, font, fontSize, timeX, y, time);
        }
        float currentY = y;
        for (String line : wrappedText) {
            showTextLine(canvas, font, fontSize, textX, currentY, line);
            currentY -= lineHeight;
        }
        if (nurse != null && !nurse.isEmpty()) {
            showTextLine(canvas, font, fontSize, nurseX, y, nurse);
        }
        System.out.printf("[fallback-draw] page=%d row=%d y=%.2f time=(%.1f,%.1f) text=(%.1f,%.1f) nurse=(%.1f,%.1f)%n",
                pageIndex1Based, row, y, timeX, y, textX, y, nurseX, y);
    }

    private static void showTextLine(PdfContentByte canvas, BaseFont font, float fontSize,
            float x, float y, String value) throws IOException {
        canvas.beginText();
        canvas.setFontAndSize(font, fontSize);
        canvas.setTextMatrix(x, y);
        if (value != null) {
            canvas.showText(value);
        }
        canvas.endText();
    }

    private static List<String> wrapText(String content, BaseFont font, float fontSize, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (content == null) {
            lines.add("");
            return lines;
        }
        String normalized = content.replace("\r", "");
        String[] paragraphs = normalized.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            if (maxWidth <= 0) {
                lines.add(paragraph);
                continue;
            }
            if (paragraph.matches(".*\\s+.*")) {
                StringBuilder current = new StringBuilder();
                String[] words = paragraph.split("\\s+");
                for (String word : words) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    String candidate = current.length() == 0 ? word : current + " " + word;
                    float width = font.getWidthPoint(candidate, fontSize);
                    if (width > maxWidth && current.length() > 0) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                if (current.length() > 0) {
                    lines.add(current.toString());
                } else {
                    lines.add("");
                }
            } else {
                StringBuilder current = new StringBuilder();
                for (int i = 0; i < paragraph.length(); i++) {
                    char ch = paragraph.charAt(i);
                    String candidate = current.toString() + ch;
                    float width = font.getWidthPoint(candidate, fontSize);
                    if (width > maxWidth && current.length() > 0) {
                        lines.add(current.toString());
                        current = new StringBuilder();
                        current.append(ch);
                    } else {
                        current.append(ch);
                    }
                }
                lines.add(current.toString());
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private FieldResolution resolveOrInjectTextField(
            PdfStamper stamper,
            AcroFields af,
            int row,
            String[] candidates,
            Rectangle rect,
            int page,
            BaseFont bf,
            float fontSize
    ) throws Exception {
        for (String c : candidates) {
            String name = String.format(c, row);
            if (af.getFieldItem(name) != null) {
                return new FieldResolution(name, false);
            }
        }
        String name = String.format(candidates[0], row);
        TextField tf = new TextField(stamper.getWriter(), rect, name);
        tf.setOptions(TextField.EDIT);
        tf.setFont(bf);
        tf.setFontSize(fontSize);
        tf.setAlignment(Element.ALIGN_LEFT);
        PdfFormField f = tf.getTextField();
        f.setFlags(PdfAnnotation.FLAGS_PRINT);
        stamper.addAnnotation(f, page);
        af.setFieldProperty(name, "textfont", bf, null);
        af.setFieldProperty(name, "textsize", fontSize, null);
        af.setGenerateAppearances(true);
        af.regenerateField(name);
        return new FieldResolution(name, true);
    }

    private static final class FieldResolution {
        final String name;
        final boolean created;

        private FieldResolution(String name, boolean created) {
            this.name = name;
            this.created = created;
        }
    }

    private void ensureAcroFormIText5(PdfReader reader, PdfStamper stamper, BaseFont bf) {
        PdfDictionary catalog = reader.getCatalog();
        PdfDictionary acro = catalog.getAsDict(PdfName.ACROFORM);
        if (acro == null) {
            acro = new PdfDictionary();
            catalog.put(PdfName.ACROFORM, acro);
            stamper.markUsed(catalog);
        }
        if (acro.get(PdfName.DA) == null) {
            acro.put(PdfName.DA, new PdfString("/Helv 12 Tf 0 g"));
        }
        PdfDictionary dr = acro.getAsDict(PdfName.DR);
        if (dr == null) {
            dr = new PdfDictionary();
            acro.put(PdfName.DR, dr);
        }
        stamper.markUsed(acro);
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

}
