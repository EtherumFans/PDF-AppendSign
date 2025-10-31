package com.demo.pdf;

import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfAnnotation;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfIndirectReference;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfString;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.TextField;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.DigestAlgorithms;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PdfSigLockDictionary;
import com.itextpdf.text.pdf.security.PrivateKeySignature;
import com.itextpdf.text.pdf.security.TSAClient;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
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

        int row = params.getRow();
        int pageIndex = params.getPageIndex();
        float yBase = params.getTableTopY() - (row - 1) * params.getRowHeight();
        String signFieldName = resolveSignatureFieldName(params);
        Rectangle signatureRect = computeSignatureRectangle(params, yBase);

        dumpSignatures("BEFORE", params.getSource());

        BaseFont formFont = resolveBaseFont(params.getCjkFontPath());
        log.info("[sign-row] Using font for text artifacts: {}", formFont.getPostscriptFontName());
        BaseFont appearanceBaseFont = resolveCjkBaseFont(firstNonBlank(params.getFontPath(), params.getCjkFontPath()),
                "NotoSansCJKsc-Regular.otf");
        Font appearanceFont = new Font(appearanceBaseFont, params.getFontSize());

        File prevFile = new File(params.getSource());
        File destFile = new File(params.getDestination());
        PdfReader reader = null;
        FileOutputStream os = null;
        PdfStamper stamper = null;
        boolean signDetachedCalled = false;
        boolean signCompleted = false;

        try {
            reader = new PdfReader(params.getSource());
            PdfDictionary perms = reader.getCatalog().getAsDict(PdfName.PERMS);
            PdfDictionary docMdpDict = perms != null ? perms.getAsDict(PdfName.DOCMDP) : null;
            Integer docMdpPerm = getDocMdpPermission(reader);
            if (docMdpDict != null || docMdpPerm != null) {
                String permText = docMdpPerm == null ? "unknown" : docMdpPerm.toString();
                throw new IllegalStateException(String.format(
                        "Document is certified with DocMDP permission P=%s. Route A requires approval signatures only.",
                        permText));
            }
            logPreSigningState(reader, prevFile);

            Rectangle pageRect = requirePageRectangle(reader, pageIndex);
            validateRectangle(signatureRect, pageRect, "signature");

            os = new FileOutputStream(params.getDestination());
            stamper = PdfStamper.createSignature(reader, os, '\0', null, true);
            log.info("[sign-row] createSignature append=true");

            ensureAcroFormIText5(reader, stamper, formFont);
            ensureAcroFormSigFlags(stamper);

            AcroFields acroFields = stamper.getAcroFields();
            acroFields.addSubstitutionFont(formFont);

            String timeValue = safe(params.getTimeValue());
            String textValue = safe(params.getTextValue());
            String nurseValue = safe(params.getNurse());

            if (params.isFallbackDraw()) {
                drawRowTextsOnPage(stamper, pageIndex, row, yBase,
                        params.getTimeX(), params.getTextX(), params.getNurseX(), params.getFontSize(),
                        timeValue, textValue, nurseValue, appearanceBaseFont);
            } else {
                ensureOrUpdateRowTextFields(stamper, pageIndex, row, yBase,
                        params.getTimeX(), params.getTextX(), params.getNurseX(), params.getFontSize(),
                        timeValue, textValue, nurseValue, formFont);
            }

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setReason(firstNonBlank(params.getReason(), "Nursing record approval"));
            appearance.setLocation(firstNonBlank(params.getLocation(), "Ward"));
            if (params.getContact() != null && !params.getContact().isBlank()) {
                appearance.setContact(params.getContact());
            }
            appearance.setSignDate(Calendar.getInstance());
            appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION);
            appearance.setLayer2Font(appearanceFont);
            appearance.setLayer2Text(buildLayer2Text(params));

            ensureSignatureField(stamper, signatureRect, pageIndex, signFieldName, params.isSignVisible());
            appearance.setVisibleSignature(signFieldName);
            if (params.isSignVisible()) {
                log.info("[sign-row] setVisibleSignature field='{}' page={} rect={} fallbackDraw={}",
                        signFieldName, pageIndex, describeRect(signatureRect), params.isFallbackDraw());
            } else {
                log.info("[sign-row] setVisibleSignature (invisible) field='{}' page={} fallbackDraw={}",
                        signFieldName, pageIndex, params.isFallbackDraw());
            }

            if (!params.isFallbackDraw()) {
                attachRowFieldLock(appearance, row);
            }

            KeyMaterial keyMaterial = loadKeyMaterial(params);
            TSAClient tsaClient = buildTsaClient(params);

            signDetachedCalled = true;
            signDetachedWithBC(appearance, keyMaterial.privateKey, keyMaterial.chain, tsaClient);
            signCompleted = true;
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

        if (signCompleted) {
            dumpSignatures("AFTER", params.getDestination());
            try {
                long prefixLen = computePrevRevisionLength(prevFile, destFile);
                log.info("[sign-row] prev='{}' ({}B) curr='{}' ({}B) prefixLen={}B", prevFile.getAbsolutePath(),
                        prevFile.exists() ? prevFile.length() : -1,
                        destFile.getAbsolutePath(), destFile.exists() ? destFile.length() : -1, prefixLen);
                assertPrefixUnchanged(prevFile, destFile, prefixLen, log);
            } catch (IOException ioException) {
                throw new IllegalStateException("Failed to validate incremental prefix", ioException);
            }
            validateSignedDocument(params.getDestination(), signFieldName, pageIndex);
        }
    }

    private void signDetachedWithBC(PdfSignatureAppearance appearance, PrivateKey privateKey,
            Certificate[] chain, TSAClient tsaClient)
            throws GeneralSecurityException, IOException, DocumentException {
        ExternalDigest digest = new BouncyCastleDigest();
        ExternalSignature signature = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256,
                BouncyCastleProvider.PROVIDER_NAME);
        MakeSignature.signDetached(appearance, digest, signature, chain, null, null, tsaClient, 0,
                MakeSignature.CryptoStandard.CADES);
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

    private static void ensureAcroFormSigFlags(PdfStamper stamper) {
        PdfDictionary catalog = stamper.getReader().getCatalog();
        PdfDictionary acro = catalog.getAsDict(PdfName.ACROFORM);
        if (acro == null) {
            acro = new PdfDictionary();
            catalog.put(PdfName.ACROFORM, acro);
        }
        acro.put(PdfName.SIGFLAGS, new PdfNumber(3));
        stamper.markUsed(catalog);
        stamper.markUsed(acro);
    }

    private PdfFormField ensureSignatureField(PdfStamper stamper, Rectangle rect,
                                              int page, String fieldName, boolean visible) throws Exception {
        AcroFields af = stamper.getAcroFields();
        int type = af.getFieldType(fieldName);
        if (type == AcroFields.FIELD_TYPE_SIGNATURE) {
            return null;
        }
        if (type != AcroFields.FIELD_TYPE_NONE) {
            throw new IllegalStateException("Field '" + fieldName + "' exists but is not a signature field");
        }
        PdfFormField sig = PdfFormField.createSignature(stamper.getWriter());
        sig.setFieldName(fieldName);
        int flags = PdfAnnotation.FLAGS_PRINT;
        if (!visible) {
            flags |= PdfAnnotation.FLAGS_INVISIBLE | PdfAnnotation.FLAGS_HIDDEN;
        }
        sig.setFlags(flags);
        sig.setPage(page);
        sig.setWidget(rect, PdfAnnotation.HIGHLIGHT_OUTLINE);
        stamper.addAnnotation(sig, page);
        log.info("[form] created signature field='{}' page={} rect={}", fieldName, page, rect);
        return sig;
    }

    private void ensureOrUpdateRowTextFields(PdfStamper stamper, int page, int row,
                                             float baseY, float timeX, float textX,
                                             float nurseX, float fontSize,
                                             String time, String text, String nurse,
                                             BaseFont bf) throws Exception {
        PdfWriter writer = stamper.getWriter();
        AcroFields af = stamper.getAcroFields();

        String fTime = "row" + row + ".time";
        String fText = "row" + row + ".text";
        String fNurse = "row" + row + ".nurse";

        Rectangle rTime = new Rectangle(timeX, baseY - fontSize - 2, timeX + 54, baseY + 4);
        Rectangle rText = new Rectangle(textX, baseY - fontSize - 2, textX + 330, baseY + 4);
        Rectangle rNurse = new Rectangle(nurseX, baseY - fontSize - 2, nurseX + 120, baseY + 4);

        Rectangle pageRect = stamper.getReader().getPageSize(page);
        validateRectangle(rTime, pageRect, fTime);
        validateRectangle(rText, pageRect, fText);
        validateRectangle(rNurse, pageRect, fNurse);

        if (af.getFieldType(fTime) == AcroFields.FIELD_TYPE_NONE) {
            TextField t = new TextField(writer, rTime, fTime);
            t.setFont(bf);
            t.setFontSize(fontSize);
            t.setOptions(TextField.READ_ONLY);
            t.setText(time);
            PdfFormField ff = t.getTextField();
            ff.setFlags(PdfAnnotation.FLAGS_PRINT);
            stamper.addAnnotation(ff, page);
            log.info("[form] created field='{}' page={} rect={}", fTime, page, rTime);
        } else {
            af.setFieldProperty(fTime, "textfont", bf, null);
            af.setFieldProperty(fTime, "textsize", fontSize, null);
            af.setField(fTime, time);
            af.setFieldProperty(fTime, "setfflags", PdfFormField.FF_READ_ONLY, null);
        }

        if (af.getFieldType(fText) == AcroFields.FIELD_TYPE_NONE) {
            TextField t = new TextField(writer, rText, fText);
            t.setFont(bf);
            t.setFontSize(fontSize);
            t.setOptions(TextField.READ_ONLY | TextField.MULTILINE);
            t.setText(text);
            PdfFormField ff = t.getTextField();
            ff.setFlags(PdfAnnotation.FLAGS_PRINT);
            stamper.addAnnotation(ff, page);
            log.info("[form] created field='{}' page={} rect={}", fText, page, rText);
        } else {
            af.setFieldProperty(fText, "textfont", bf, null);
            af.setFieldProperty(fText, "textsize", fontSize, null);
            af.setField(fText, text);
            af.setFieldProperty(fText, "setfflags", PdfFormField.FF_READ_ONLY | PdfFormField.FF_MULTILINE, null);
        }

        if (af.getFieldType(fNurse) == AcroFields.FIELD_TYPE_NONE) {
            TextField t = new TextField(writer, rNurse, fNurse);
            t.setFont(bf);
            t.setFontSize(fontSize);
            t.setOptions(TextField.READ_ONLY);
            t.setText(nurse);
            PdfFormField ff = t.getTextField();
            ff.setFlags(PdfAnnotation.FLAGS_PRINT);
            stamper.addAnnotation(ff, page);
            log.info("[form] created field='{}' page={} rect={}", fNurse, page, rNurse);
        } else {
            af.setFieldProperty(fNurse, "textfont", bf, null);
            af.setFieldProperty(fNurse, "textsize", fontSize, null);
            af.setField(fNurse, nurse);
            af.setFieldProperty(fNurse, "setfflags", PdfFormField.FF_READ_ONLY, null);
        }
    }

    private void attachRowFieldLock(PdfSignatureAppearance appearance, int row) {
        if (appearance == null) {
            return;
        }

        String prefix = "row" + row + ".";
        PdfSigLockDictionary lock = new PdfSigLockDictionary(
                PdfSigLockDictionary.LockPermissions.INCLUDE,
                new String[]{prefix + "time", prefix + "text", prefix + "nurse"}
        );

        if (!applyFieldLockDictionary(appearance, lock)) {
            log.warn("[sign-row] PdfSignatureAppearance#setFieldLockDictionary unavailable; row {} fields remain editable",
                    row);
        }
    }

    private boolean applyFieldLockDictionary(PdfSignatureAppearance appearance, PdfSigLockDictionary lock) {
        try {
            Method method = PdfSignatureAppearance.class.getMethod("setFieldLockDictionary", PdfSigLockDictionary.class);
            method.invoke(appearance, lock);
            return true;
        } catch (NoSuchMethodException missing) {
            return tryLegacyFieldLock(appearance, lock, missing);
        } catch (ReflectiveOperationException ex) {
            log.debug("[sign-row] Unable to apply field lock dictionary via reflection: {}", ex.toString());
            return false;
        }
    }

    private boolean tryLegacyFieldLock(PdfSignatureAppearance appearance, PdfSigLockDictionary lock, Exception missing) {
        try {
            Method legacy = PdfSignatureAppearance.class.getMethod(
                    "setFieldLock",
                    PdfSigLockDictionary.LockPermissions.class,
                    String[].class
            );
            legacy.invoke(appearance, lock.getPermission(), lock.getFields());
            return true;
        } catch (ReflectiveOperationException legacyEx) {
            log.debug("[sign-row] Field lock methods unavailable ({} / {})",
                    missing.toString(), legacyEx.toString());
            return false;
        }
    }

    private void drawRowTextsOnPage(PdfStamper stamper, int page, int row,
                                    float baseY, float timeX, float textX,
                                    float nurseX, float fontSize,
                                    String time, String text, String nurse,
                                    BaseFont bf) {
        PdfContentByte cb = stamper.getOverContent(page);
        cb.saveState();
        cb.beginText();
        cb.setFontAndSize(bf, fontSize);
        cb.showTextAligned(Element.ALIGN_LEFT, time, timeX, baseY, 0);
        cb.showTextAligned(Element.ALIGN_LEFT, text, textX, baseY, 0);
        cb.showTextAligned(Element.ALIGN_LEFT, nurse, nurseX, baseY, 0);
        cb.endText();
        cb.restoreState();
    }

    private void validateSignedDocument(String path, String sigFieldName, int pageIndex) throws Exception {
        File signedFile = new File(path);
        if (!signedFile.exists()) {
            throw new IllegalStateException("Signed file not found: " + path);
        }
        try (FileInputStream fis = new FileInputStream(signedFile)) {
            byte[] header = new byte[5];
            if (fis.read(header) != 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D'
                    || header[3] != 'F' || header[4] != '-') {
                throw new IllegalStateException("PDF header is not at byte 0 for " + path);
            }
        }

        PdfReader reader = new PdfReader(path);
        try {
            AcroFields af = reader.getAcroFields();
            if (af.getFieldItem(sigFieldName) == null) {
                throw new IllegalStateException("Signature field '" + sigFieldName + "' missing after signing");
            }
            if (af.getFieldType(sigFieldName) != AcroFields.FIELD_TYPE_SIGNATURE) {
                throw new IllegalStateException("Field '" + sigFieldName + "' is not a signature field");
            }

            PdfDictionary sigDict = af.getSignatureDictionary(sigFieldName);
            if (sigDict == null) {
                throw new IllegalStateException("Signature dictionary missing for field '" + sigFieldName + "'");
            }
            if (!PdfName.ADOBE_PPKLITE.equals(sigDict.getAsName(PdfName.FILTER))) {
                throw new IllegalStateException("Unexpected signature filter in field '" + sigFieldName + "'");
            }
            if (!PdfName.ADBE_PKCS7_DETACHED.equals(sigDict.getAsName(PdfName.SUBFILTER))) {
                throw new IllegalStateException("Unexpected signature subfilter in field '" + sigFieldName + "'");
            }

            PdfArray byteRange = sigDict.getAsArray(PdfName.BYTERANGE);
            if (byteRange == null || byteRange.size() != 4) {
                throw new IllegalStateException("Invalid ByteRange for signature field '" + sigFieldName + "'");
            }
            if (byteRange.getAsNumber(0).longValue() != 0) {
                throw new IllegalStateException("ByteRange must start at 0 for field '" + sigFieldName + "'");
            }
            long len1 = byteRange.getAsNumber(1).longValue();
            long start2 = byteRange.getAsNumber(2).longValue();
            long len2 = byteRange.getAsNumber(3).longValue();
            if (len1 < 0 || start2 < len1) {
                throw new IllegalStateException("ByteRange discontinuity for field '" + sigFieldName + "'");
            }
            if (len2 < 0) {
                throw new IllegalStateException("Negative ByteRange length for field '" + sigFieldName + "'");
            }
            long fileLength = reader.getFileLength();
            if (start2 + len2 > fileLength) {
                throw new IllegalStateException("ByteRange extends beyond file length for field '" + sigFieldName + "'");
            }

            PdfString contents = sigDict.getAsString(PdfName.CONTENTS);
            if (contents == null) {
                throw new IllegalStateException("Signature Contents missing for field '" + sigFieldName + "'");
            }
            if (contents.getOriginalBytes().length % 2 != 0) {
                throw new IllegalStateException("Signature Contents length must be even hex for field '" + sigFieldName + "'");
            }

            AcroFields.Item item = af.getFieldItem(sigFieldName);
            PdfDictionary widget = item.getWidget(0);
            PdfNumber widgetFlags = widget.getAsNumber(PdfName.F);
            if (widgetFlags == null || (widgetFlags.intValue() & PdfAnnotation.FLAGS_PRINT) == 0) {
                throw new IllegalStateException("Signature widget for field '" + sigFieldName + "' lacks PRINT flag");
            }

            PdfDictionary pageDict = reader.getPageN(pageIndex);
            PdfArray annots = pageDict != null ? pageDict.getAsArray(PdfName.ANNOTS) : null;
            boolean widgetFound = false;
            if (annots != null) {
                for (int i = 0; i < annots.size(); i++) {
                    PdfIndirectReference ref = annots.getAsIndirectObject(i);
                    if (ref != null && ref.equals(item.getWidgetRef(0))) {
                        widgetFound = true;
                        break;
                    }
                }
            }
            if (!widgetFound) {
                throw new IllegalStateException("Signature widget for field '" + sigFieldName + "' not listed in page annots");
            }
        } finally {
            reader.close();
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

    private void logPreSigningState(PdfReader reader, File prevFile) {
        try {
            AcroFields af = reader.getAcroFields();
            java.util.List<String> names = af.getSignatureNames();
            String byteRangeDesc = "n/a";
            if (!names.isEmpty()) {
                PdfDictionary sigDict = af.getSignatureDictionary(names.get(0));
                PdfArray br = sigDict != null ? sigDict.getAsArray(PdfName.BYTERANGE) : null;
                byteRangeDesc = describeByteRange(br);
            }
            long prevLen = (prevFile != null && prevFile.exists()) ? prevFile.length() : -1L;
            log.info("[sign-row] existing signatures count={} firstByteRange={} prevLen={}B prevPath='{}'", names.size(),
                    byteRangeDesc, prevLen, prevFile == null ? "n/a" : prevFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("[sign-row] Failed to log pre-signing state: {}", e.toString());
        }
    }

    private static String describeByteRange(PdfArray br) {
        if (br == null || br.size() != 4) {
            return "n/a";
        }
        return String.format("[%s, %s, %s, %s]",
                br.getAsNumber(0), br.getAsNumber(1), br.getAsNumber(2), br.getAsNumber(3));
    }

    private static long computePrevRevisionLength(File prev, File curr) throws IOException {
        if (prev != null && prev.exists()) {
            return prev.length();
        }
        if (curr == null || !curr.exists()) {
            throw new IOException("Current file not found while computing previous revision length: " + curr);
        }
        com.itextpdf.text.pdf.PdfReader r = null;
        try {
            r = new com.itextpdf.text.pdf.PdfReader(curr.getAbsolutePath());
            com.itextpdf.text.pdf.AcroFields af = r.getAcroFields();
            java.util.List<String> names = af.getSignatureNames();
            if (names.isEmpty()) {
                throw new IOException("No signatures found in current document; cannot determine previous revision boundary.");
            }
            long minEnd = Long.MAX_VALUE;
            for (String name : names) {
                com.itextpdf.text.pdf.PdfDictionary sig = af.getSignatureDictionary(name);
                if (sig == null) {
                    continue;
                }
                com.itextpdf.text.pdf.PdfArray br = sig.getAsArray(com.itextpdf.text.pdf.PdfName.BYTERANGE);
                if (br == null || br.size() != 4) {
                    continue;
                }
                long start2 = br.getAsNumber(2).longValue();
                long len2 = br.getAsNumber(3).longValue();
                long end = start2 + len2;
                if (end < minEnd) {
                    minEnd = end;
                }
            }
            if (minEnd == Long.MAX_VALUE) {
                throw new IOException("Unable to compute previous revision length from signatures.");
            }
            return minEnd;
        } finally {
            if (r != null) {
                r.close();
            }
        }
    }

    private static void assertPrefixUnchanged(File prev, File curr, long prefixLen, Logger log) throws IOException {
        if (prefixLen < 0) {
            throw new IllegalArgumentException("Prefix length must be >= 0");
        }
        if (prev == null || !prev.exists()) {
            throw new IOException("Previous revision file missing for incremental check: " + prev);
        }
        if (curr == null || !curr.exists()) {
            throw new IOException("Current file missing for incremental check: " + curr);
        }
        try (BufferedInputStream in1 = new BufferedInputStream(new FileInputStream(prev));
             BufferedInputStream in2 = new BufferedInputStream(new FileInputStream(curr))) {
            long pos = 0;
            while (pos < prefixLen) {
                int b1 = in1.read();
                int b2 = in2.read();
                if (b1 != b2) {
                    throw new IllegalStateException(
                            String.format("NON-INCREMENTAL CHANGE DETECTED at offset %d: prev=0x%02X, curr=0x%02X. "
                                            + "Your second signing rewrote earlier bytes. Remove any full-save/flatten/compression and keep all ops in a single iText append-mode signing session.",
                                    pos, b1, b2));
                }
                pos++;
            }
        }
        log.info("[INCREMENTAL-CHECK] OK. New file keeps first {} bytes identical to previous revision.", prefixLen);
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
