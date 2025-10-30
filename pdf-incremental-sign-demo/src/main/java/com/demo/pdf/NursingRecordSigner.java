package com.demo.pdf;

import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.Objects;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfContentByte;

/**
 * Helper that fills a nursing record row and signs it incrementally.
 */
public final class NursingRecordSigner {

    private static final Logger log = LoggerFactory.getLogger(NursingRecordSigner.class);

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
        private String signFieldTemplate = "sig_row_%d";
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

        log.info("[sign-row] src={}, dest={}, row={}, time='{}', nurse='{}'",
                params.getSource(), params.getDestination(), params.getRow(), params.getTimeValue(),
                params.getNurse());
        log.info("[sign-row] fallbackDraw={}, page={}, tableTopY={}, rowHeight={}, timeX={}, textX={}, nurseX={}, textMaxW={}, fontSize={}",
                params.isFallbackDraw(), params.getPageIndex(), params.getTableTopY(), params.getRowHeight(),
                params.getTimeX(), params.getTextX(), params.getNurseX(), params.getTextMaxWidth(),
                params.getFontSize());

        if (params.isFallbackDraw()) {
            signRowWithFallbackDrawing(params);
            return;
        }

        int row = params.getRow();
        Rectangle timeRect = rectForTime(row);
        Rectangle textRect = rectForText(row);
        Rectangle nurseRect = rectForNurse(row);
        int pageIndex = params.getPageIndex();
        float tableTopY = params.getTableTopY();
        float rowHeight = params.getRowHeight();
        float yBase = tableTopY - (row - 1) * rowHeight;

        dumpSignatures("BEFORE", params.getSource());

        BaseFont cjkFont = resolveBaseFont(params.getCjkFontPath());
        log.info("[sign-row] Using font for text fields: {}", cjkFont.getPostscriptFontName());
        Font appearanceFont = new Font(cjkFont, 10f);

        PdfReader reader = null;
        FileOutputStream os = null;
        PdfStamper stamper = null;
        boolean signDetachedCalled = false;

        try {
            reader = new PdfReader(params.getSource());
            os = new FileOutputStream(params.getDestination());

            Rectangle pageRect = requirePageRectangle(reader, pageIndex);
            validateRectangle(timeRect, pageRect, "time");
            validateRectangle(textRect, pageRect, "text");
            validateRectangle(nurseRect, pageRect, "nurse");
            stamper = PdfStamper.createSignature(reader, os, '\0', null, true);
            log.info("[sign-row] createSignature append=true");

            AcroFields acroFields = stamper.getAcroFields();
            ensureAcroFormIText5(reader, stamper, cjkFont);
            acroFields.addSubstitutionFont(cjkFont);
            acroFields.setGenerateAppearances(true);

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

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            String signFieldName = resolveSignatureFieldName(params);
            AcroFields af = reader.getAcroFields();
            boolean hasField = af.getFieldItem(signFieldName) != null;
            boolean hasAnySignatures = !af.getSignatureNames().isEmpty();

            boolean doCertify = !hasAnySignatures || params.isCertifyP3();
            if (hasAnySignatures && params.isCertifyP3()) {
                log.warn("[sign-row] Document already has signatures. Ignoring certifyP3 for subsequent signatures.");
                doCertify = false;
            }

            if (doCertify) {
                int level;
                if (params.isCertifyP3()) {
                    level = PdfSignatureAppearance.CERTIFIED_FORM_FILLING_AND_ANNOTATIONS;
                    log.info("[sign-row] Applying DocMDP as FIRST signature (P=3).");
                } else {
                    level = PdfSignatureAppearance.CERTIFIED_FORM_FILLING;
                    log.info("[sign-row] Applying DocMDP as FIRST signature (P=2).");
                }
                appearance.setCertificationLevel(level);
            } else {
                log.info("[sign-row] Approval signature (no DocMDP).");
            }

            if (params.isSignVisible()) {
                if (hasField) {
                    log.info("[sign-row] visible-sign field='{}' page={} rect=n/a certifyP3={}",
                            signFieldName, pageIndex, params.isCertifyP3());
                    appearance.setVisibleSignature(signFieldName);
                } else {
                    Rectangle signatureRect = computeSignatureRectangle(params, yBase);
                    float signX = signatureRect.getLeft();
                    float signY = signatureRect.getBottom();
                    float signW = signatureRect.getWidth();
                    float signH = signatureRect.getHeight();
                    log.info("[sign-row] visible-sign field='{}' page={} rect=[{},{} ,{} ,{}] certifyP3={}",
                            signFieldName, pageIndex, signX, signY, signX + signW, signY + signH,
                            params.isCertifyP3());
                    appearance.setVisibleSignature(signatureRect, pageIndex, signFieldName);
                }
            } else {
                log.info("[sign-row] visible-sign field='{}' page={} rect=n/a certifyP3={} (invisible)",
                        signFieldName, pageIndex, params.isCertifyP3());
                appearance.setVisibleSignature(signFieldName);
            }

            appearance.setReason(params.getReason());
            appearance.setLocation(params.getLocation());
            appearance.setContact(params.getContact());
            appearance.setSignDate(Calendar.getInstance());
            appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION);
            appearance.setLayer2Font(appearanceFont);
            appearance.setLayer2Text(buildLayer2Text(params));

            KeyMaterial keyMaterial = loadKeyMaterial(params);
            TSAClient tsaClient = buildTsaClient(params);

            signDetachedCalled = true;
            signDetachedWithBC(appearance, keyMaterial.privateKey, keyMaterial.chain, tsaClient);

            dumpSignatures("AFTER", params.getDestination());
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

    private void signRowWithFallbackDrawing(SignParams params) throws Exception {
        log.info("[sign-row:fallback] src={}, dest={}, row={}, time='{}', nurse='{}'",
                params.getSource(), params.getDestination(), params.getRow(), params.getTimeValue(),
                params.getNurse());
        log.info("[sign-row:fallback] page={}, tableTopY={}, rowHeight={}, timeX={}, textX={}, nurseX={}, textMaxW={}, fontSize={}",
                params.getPageIndex(), params.getTableTopY(), params.getRowHeight(), params.getTimeX(),
                params.getTextX(), params.getNurseX(), params.getTextMaxWidth(), params.getFontSize());

        dumpSignatures("BEFORE", params.getSource());

        PdfReader reader = null;
        FileOutputStream os = null;
        PdfStamper stamper = null;
        boolean signDetachedCalled = false;
        try {
            reader = new PdfReader(params.getSource());
            AcroFields af = reader.getAcroFields();
            boolean hasAnySignatures = !af.getSignatureNames().isEmpty();

            int pageIndex = params.getPageIndex();
            requirePageRectangle(reader, pageIndex);

            os = new FileOutputStream(params.getDestination());
            stamper = PdfStamper.createSignature(reader, os, '\0', null, true);
            log.info("[sign-row:fallback] createSignature append=true");

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            if (params.getReason() != null) {
                appearance.setReason(params.getReason());
            }
            if (params.getLocation() != null) {
                appearance.setLocation(params.getLocation());
            }
            if (params.getContact() != null) {
                appearance.setContact(params.getContact());
            }
            appearance.setSignDate(Calendar.getInstance());
            appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION);

            BaseFont drawFont = resolveCjkBaseFont(firstNonBlank(params.getFontPath(), params.getCjkFontPath()),
                    "NotoSansCJKsc-Regular.otf");
            Font signatureFont = new Font(drawFont, 10f);
            appearance.setLayer2Font(signatureFont);
            appearance.setLayer2Text(buildLayer2Text(params));

            boolean doCertify = !hasAnySignatures || params.isCertifyP3();
            if (hasAnySignatures && params.isCertifyP3()) {
                log.warn("[sign-row] Document already has signatures. Ignoring certifyP3 for subsequent signatures.");
                doCertify = false;
            }

            if (doCertify) {
                int level;
                if (params.isCertifyP3()) {
                    level = PdfSignatureAppearance.CERTIFIED_FORM_FILLING_AND_ANNOTATIONS;
                    log.info("[sign-row] Applying DocMDP as FIRST signature (P=3).");
                } else {
                    level = PdfSignatureAppearance.CERTIFIED_FORM_FILLING;
                    log.info("[sign-row] Applying DocMDP as FIRST signature (P=2).");
                }
                appearance.setCertificationLevel(level);
            } else {
                log.info("[sign-row] Approval signature (no DocMDP).");
            }

            float rowBaselineY = params.getTableTopY() - (params.getRow() - 1) * params.getRowHeight();
            PdfDictionary perms = reader.getCatalog().getAsDict(PdfName.PERMS);
            boolean certified = perms != null && perms.getAsDict(PdfName.DOCMDP) != null;
            if (certified) {
                // 禁用：新增注释在 DocMDP=P=2 下不被允许，会让前一修订签名失效
                // applyFallbackAnnotations(stamper, pageIndex, rowBaselineY, /* ... */);
            }
            drawRowFallback(stamper, params, drawFont);

            float yBase = rowBaselineY;
            String signFieldName = resolveSignatureFieldName(params);
            boolean hasField = af.getFieldItem(signFieldName) != null;
            if (params.isSignVisible()) {
                if (hasField) {
                    log.info("[sign-row] visible-sign field='{}' page={} rect=n/a certifyP3={}",
                            signFieldName, pageIndex, params.isCertifyP3());
                    appearance.setVisibleSignature(signFieldName);
                } else {
                    Rectangle signatureRect = computeSignatureRectangle(params, yBase);
                    float signX = signatureRect.getLeft();
                    float signY = signatureRect.getBottom();
                    float signW = signatureRect.getWidth();
                    float signH = signatureRect.getHeight();
                    log.info("[sign-row] visible-sign field='{}' page={} rect=[{},{} ,{} ,{}] certifyP3={}",
                            signFieldName, pageIndex, signX, signY, signX + signW, signY + signH,
                            params.isCertifyP3());
                    appearance.setVisibleSignature(signatureRect, pageIndex, signFieldName);
                }
            } else {
                log.info("[sign-row] visible-sign field='{}' page={} rect=n/a certifyP3={} (invisible)",
                        signFieldName, pageIndex, params.isCertifyP3());
                appearance.setVisibleSignature(signFieldName);
            }

            KeyMaterial keyMaterial = loadKeyMaterial(params);
            TSAClient tsaClient = buildTsaClient(params);

            signDetachedCalled = true;
            signDetachedWithBC(appearance, keyMaterial.privateKey, keyMaterial.chain, tsaClient);

            dumpSignatures("AFTER", params.getDestination());
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

    private void signDetachedWithBC(PdfSignatureAppearance appearance, PrivateKey privateKey,
            Certificate[] chain, TSAClient tsaClient)
            throws GeneralSecurityException, IOException, DocumentException {
        ExternalDigest digest = new BouncyCastleDigest();
        ExternalSignature signature = new PrivateKeySignature(privateKey, "SHA256",
                BouncyCastleProvider.PROVIDER_NAME);
        MakeSignature.signDetached(appearance, digest, signature, chain, null, null, tsaClient, 0,
                MakeSignature.CryptoStandard.CMS);
    }

    private KeyMaterial loadKeyMaterial(SignParams params) throws Exception {
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
        return new KeyMaterial(privateKey, chain);
    }

    private TSAClient buildTsaClient(SignParams params) {
        if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
            return new TSAClientBouncyCastle(params.getTsaUrl());
        }
        return null;
    }

    private static String dumpFieldNames(AcroFields af) {
        return String.valueOf(af.getFields().keySet());
    }

    private static String resolveSignatureFieldName(SignParams params) {
        String template = params.getSignFieldTemplate();
        if (template != null && !template.isBlank()) {
            if (template.contains("%")) {
                return String.format(template, params.getRow());
            }
            return template.replace("{row}", String.valueOf(params.getRow()));
        }
        return String.format("sig_row_%d", params.getRow());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Rectangle computeSignatureRectangle(SignParams params, float yBase) {
        if (params.getSignX() >= 0f) {
            float signX = params.getSignX();
            float width = params.getSignWidth() > 0 ? params.getSignWidth() : 1f;
            float height = params.getSignHeight() > 0 ? params.getSignHeight() : 1f;
            float bottom = yBase + params.getSignYOffset();
            return new Rectangle(signX, bottom, signX + width, bottom + height);
        }
        Rectangle defaultRect = rectForSignature(params.getRow());
        return new Rectangle(defaultRect);
    }

    private void applyFallbackAnnotations(
            com.itextpdf.text.pdf.PdfStamper stamper,
            int pageIndex,
            float rowBaselineY,
            float timeX,
            float textX,
            float nurseX,
            float rowHeight,
            String timeText,
            String noteText,
            String nurseName,
            com.itextpdf.text.pdf.BaseFont bf,
            float fontSize
    ) throws Exception {
        log.info("[fallback-annot] disabled: honoring DocMDP=P=2 (form fill-in & signing only). No annotations will be added.");
        // Intentionally no-op to keep the certification chain intact.
    }

    private void drawRowFallback(PdfStamper stamper, SignParams params, BaseFont font)
            throws DocumentException {
        int pageIndex1Based = params.getPageIndex();
        int numberOfPages = stamper.getReader().getNumberOfPages();
        if (pageIndex1Based < 1 || pageIndex1Based > numberOfPages) {
            throw new IllegalArgumentException("Page index " + pageIndex1Based + " out of bounds (1-"
                    + numberOfPages + ")");
        }
        int row = params.getRow();
        if (row < 1) {
            throw new IllegalArgumentException("Row index must be >= 1");
        }
        float baselineY = params.getTableTopY() - (row - 1) * params.getRowHeight();
        float fontSize = params.getFontSize() > 0 ? params.getFontSize() : 10f;
        PdfContentByte canvas = stamper.getOverContent(pageIndex1Based);
        if (canvas == null) {
            throw new IllegalStateException("Unable to obtain over content for page " + pageIndex1Based);
        }

        Font drawFont = new Font(font, fontSize);
        String time = safe(params.getTimeValue());
        if (!time.isEmpty()) {
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(time, drawFont),
                    params.getTimeX(), baselineY, 0f);
        }

        String text = safe(params.getTextValue());
        if (!text.isEmpty()) {
            float maxWidth = params.getTextMaxWidth() > 0 ? params.getTextMaxWidth() : 330f;
            float textTop = baselineY + params.getRowHeight() - 2f;
            ColumnText columnText = new ColumnText(canvas);
            columnText.setSimpleColumn(new Phrase(text, drawFont),
                    params.getTextX(), baselineY, params.getTextX() + maxWidth, textTop, 0,
                    Element.ALIGN_LEFT);
            columnText.go();
        }

        String nurse = safe(params.getNurse());
        if (!nurse.isEmpty()) {
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(nurse, drawFont),
                    params.getNurseX(), baselineY, 0f);
        }

        log.info("[fallback-draw] page={} row={} baseline={} time=({}, {}) textX={} nurse=({}, {})",
                pageIndex1Based,
                row,
                baselineY,
                params.getTimeX(),
                baselineY,
                params.getTextX(),
                params.getNurseX(),
                baselineY);
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

    private static final class KeyMaterial {
        final PrivateKey privateKey;
        final Certificate[] chain;

        private KeyMaterial(PrivateKey privateKey, Certificate[] chain) {
            this.privateKey = privateKey;
            this.chain = chain;
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

    private static void dumpSignatures(String tag, String path) {
        try {
            com.itextpdf.text.pdf.PdfReader r = new com.itextpdf.text.pdf.PdfReader(path);
            com.itextpdf.text.pdf.AcroFields af = r.getAcroFields();
            java.util.List<String> names = af.getSignatureNames();

            Integer p = getDocMdpPermission(r);
            String pText = (p == null) ? "none"
                    : (p == 1 ? "P=1 (no changes)"
                    : (p == 2 ? "P=2 (form fill-in & signing allowed)"
                    : (p == 3 ? "P=3 (annotations, form fill-in & signing allowed)"
                    : "P=" + p)));

            log.info("[{}][dump] file='{}' size={}B, signatures={}, DocMDP={}",
                    tag, path, new java.io.File(path).length(), names.size(), pText);

            int total = af.getTotalRevisions();
            for (String name : names) {
                com.itextpdf.text.pdf.PdfDictionary sigDict = af.getSignatureDictionary(name);
                com.itextpdf.text.pdf.PdfArray br = sigDict.getAsArray(com.itextpdf.text.pdf.PdfName.BYTERANGE);
                com.itextpdf.text.pdf.security.PdfPKCS7 pkcs7 = af.verifySignature(name);
                int rev = af.getRevision(name);
                boolean covers = af.signatureCoversWholeDocument(name);
                String subFilter = String.valueOf(sigDict.get(com.itextpdf.text.pdf.PdfName.SUBFILTER));
                String reason = pkcs7.getReason();
                String location = pkcs7.getLocation();
                java.util.Calendar cal = pkcs7.getSignDate();
                String when = (cal == null) ? "n/a" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ").format(cal.getTime());
                String brStr = (br == null) ? "n/a" : String.format("[%s, %s, %s, %s]",
                        br.getAsNumber(0), br.getAsNumber(1), br.getAsNumber(2), br.getAsNumber(3));

                log.info("[{}][sig] name='{}' rev={}/{} coversWholeDoc={} subFilter={} time={} reason='{}' location='{}' byteRange={}",
                        tag, name, rev, total, covers, subFilter, when, reason, location, brStr);
            }
            r.close();
        } catch (Exception e) {
            log.warn("[{}][dump] fail for {}: {}", tag, path, e.toString());
        }
    }

    private static Integer getDocMdpPermission(com.itextpdf.text.pdf.PdfReader r) {
        com.itextpdf.text.pdf.PdfDictionary catalog = r.getCatalog();
        if (catalog == null) return null;
        com.itextpdf.text.pdf.PdfDictionary perms = catalog.getAsDict(com.itextpdf.text.pdf.PdfName.PERMS);
        if (perms == null) return null;
        com.itextpdf.text.pdf.PdfDictionary docmdp = perms.getAsDict(com.itextpdf.text.pdf.PdfName.DOCMDP);
        if (docmdp == null) return null;
        com.itextpdf.text.pdf.PdfArray refArr = docmdp.getAsArray(com.itextpdf.text.pdf.PdfName.REFERENCE);
        if (refArr == null || refArr.size() == 0) return null;
        com.itextpdf.text.pdf.PdfDictionary ref = refArr.getAsDict(0);
        if (ref == null) return null;
        com.itextpdf.text.pdf.PdfDictionary tp = ref.getAsDict(com.itextpdf.text.pdf.PdfName.TRANSFORMPARAMS);
        if (tp == null) return null;
        com.itextpdf.text.pdf.PdfNumber p = tp.getAsNumber(com.itextpdf.text.pdf.PdfName.P);
        return (p == null) ? null : p.intValue();
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

    private static BaseFont resolveCjkBaseFont(String fontPath, String bundledName) throws Exception {
        if (fontPath != null && !fontPath.isEmpty()) {
            Path path = Paths.get(fontPath);
            if (Files.exists(path)) {
                return BaseFont.createFont(path.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
            log.warn("[sign-row] CJK font not found at {}; falling back to bundled font", path);
        }
        try (InputStream in = NursingRecordSigner.class.getResourceAsStream("/" + bundledName)) {
            if (in == null) {
                return BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            }
            byte[] bytes = toBytes(in);
            return BaseFont.createFont(bundledName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, bytes, null);
        }
    }

    private static byte[] toBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private BaseFont resolveBaseFont(String cjkFontPath) throws Exception {
        if (cjkFontPath != null && !cjkFontPath.isBlank()) {
            Path path = Paths.get(cjkFontPath);
            if (Files.exists(path)) {
                return BaseFont.createFont(path.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else {
                log.warn("[sign-row] CJK font not found at {}, falling back to defaults", path);
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
            log.warn("[sign-row] Failed to load embedded CJK font, falling back to Helvetica: {}", e.getMessage());
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
