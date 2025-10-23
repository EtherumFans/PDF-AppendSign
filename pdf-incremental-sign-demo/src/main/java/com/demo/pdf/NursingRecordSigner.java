package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.source.IRandomAccessSource;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import com.itextpdf.signatures.SignatureUtil;
import com.itextpdf.signatures.TSAClientBouncyCastle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NursingRecordSigner {

    private NursingRecordSigner() {
    }

    public enum SigningMode {
        TEMPLATE,
        INJECT,
        AUTO
    }

    public static class SignParams {
        private String source;
        private String destination;
        private int row;
        private String timeValue;
        private String textValue;
        private String nurse;
        private String pkcs12Path;
        private String password;
        private String tsaUrl;
        private SigningMode mode = SigningMode.AUTO;
        private int page = 1;
        private boolean certifyOnFirstInject;

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

        public String getTsaUrl() {
            return tsaUrl;
        }

        public void setTsaUrl(String tsaUrl) {
            this.tsaUrl = tsaUrl;
        }

        public SigningMode getMode() {
            return mode;
        }

        public void setMode(String modeValue) {
            if (modeValue == null || modeValue.isBlank()) {
                this.mode = SigningMode.AUTO;
                return;
            }
            switch (modeValue.toLowerCase(Locale.ROOT)) {
                case "template":
                    this.mode = SigningMode.TEMPLATE;
                    break;
                case "inject":
                    this.mode = SigningMode.INJECT;
                    break;
                case "auto":
                    this.mode = SigningMode.AUTO;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported mode: " + modeValue);
            }
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public boolean isCertifyOnFirstInject() {
            return certifyOnFirstInject;
        }

        public void setCertifyOnFirstInject(boolean certifyOnFirstInject) {
            this.certifyOnFirstInject = certifyOnFirstInject;
        }
    }

    public static void signRow(SignParams params) throws Exception {
        DemoKeystoreUtil.ensureProvider();
        Path srcPath = Path.of(params.getSource());
        Path destPath = Path.of(params.getDestination());
        long originalSize = Files.size(srcPath);

        PdfSanityUtil.requireHeader(srcPath);

        List<String> baselineNames;
        try (PdfReader baselineReader = new PdfReader(params.getSource());
             PdfDocument baselineDoc = new PdfDocument(baselineReader)) {
            baselineNames = new SignatureUtil(baselineDoc).getSignatureNames();
        }
        int initialSignatureCount = baselineNames.size();
        Set<String> baselineSet = new HashSet<>(baselineNames);

        String pkcs12Path = params.getPkcs12Path();
        char[] password = params.getPassword() != null ? params.getPassword().toCharArray() : "123456".toCharArray();
        if (pkcs12Path == null) {
            pkcs12Path = DemoKeystoreUtil.createDemoP12().toAbsolutePath().toString();
            System.out.println("[sign-row] Using generated demo PKCS#12: " + pkcs12Path);
        }
        KeyStore ks = DemoKeystoreUtil.loadKeyStore(pkcs12Path, password);
        KeyStore.PrivateKeyEntry entry = DemoKeystoreUtil.firstPrivateKey(ks, password);
        PrivateKey privateKey = entry.getPrivateKey();
        X509Certificate[] chain = Arrays.stream(entry.getCertificateChain())
                .map(cert -> (X509Certificate) cert)
                .toArray(X509Certificate[]::new);

        String sigName = "sig_row_" + params.getRow();

        SignatureFieldContext signatureContext = null;

        try (PdfReader reader = new PdfReader(params.getSource());
             FileOutputStream fos = new FileOutputStream(params.getDestination())) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            PdfDocument document = signer.getDocument();
            PdfAcroForm acro = PdfAcroForm.getAcroForm(document, true);
            FormUtil.ensureNeedAppearances(acro);
            acro.setSignatureFlags(PdfAcroForm.SIGNATURE_EXIST);

            if (params.getPage() < 1 || params.getPage() > document.getNumberOfPages()) {
                throw new IllegalArgumentException("Page " + params.getPage() + " is out of bounds");
            }

            String prefix = "row" + params.getRow();
            String timeName = prefix + ".time";
            String textName = prefix + ".text";
            String nurseName = prefix + ".nurse";

            SigningMode effectiveMode = resolveMode(params.getMode(), acro, timeName, textName, nurseName, sigName);
            System.out.println("[sign-row] Effective mode: " + effectiveMode);

            Rectangle pageSize = document.getPage(params.getPage()).getPageSize();
            PdfTextFormField timeField = resolveTextField(document, acro, params.getPage(), pageSize, params.getRow(), timeName,
                    LayoutUtil.FieldSlot.TIME, false, effectiveMode);
            PdfTextFormField textField = resolveTextField(document, acro, params.getPage(), pageSize, params.getRow(), textName,
                    LayoutUtil.FieldSlot.TEXT, true, effectiveMode);
            PdfTextFormField nurseField = resolveTextField(document, acro, params.getPage(), pageSize, params.getRow(), nurseName,
                    LayoutUtil.FieldSlot.NURSE, false, effectiveMode);
            signatureContext = ensureSignatureField(document, acro, params.getPage(),
                    params.getRow(), sigName, effectiveMode);

            if (baselineSet.contains(sigName)) {
                System.out.println("[sign-row] Warning: signing an already signed field: " + sigName);
            }

            timeField.setValue(params.getTimeValue());
            textField.setValue(params.getTextValue());
            nurseField.setValue(params.getNurse());
            timeField.setReadOnly(true);
            textField.setReadOnly(true);
            nurseField.setReadOnly(true);

            FieldLockUtil.applyIncludeLock(signatureContext.getField(), timeName, textName, nurseName);

            if (effectiveMode == SigningMode.INJECT && params.isCertifyOnFirstInject() && !DocMDPUtil.hasDocMDP(document)) {
                signer.setCertificationLevel(DocMDPUtil.Permission.FORM_FILL_AND_SIGNATURES.getCertificationLevel());
            }

            signer.setFieldName(sigName);
            PdfSignatureAppearance appearance = signer.getSignatureAppearance();
            appearance.setReuseAppearance(false);
            appearance.setPageNumber(signatureContext.getPageNumber());
            appearance.setPageRect(signatureContext.getRect());
            appearance.setReason("Nursing note " + params.getTimeValue());
            appearance.setLocation("Ward A");
            String layer2 = String.format("护士: %s\n时间: %s\n事由: Nursing note %s",
                    params.getNurse(), params.getTimeValue(), params.getTimeValue());
            PdfFont font = resolveAppearanceFont();
            appearance.setLayer2Font(font);
            appearance.setLayer2Text(layer2);

            IExternalSignature pks = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            IExternalDigest digest = new BouncyCastleDigest();
            TSAClientBouncyCastle tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }
            signer.signDetached(digest, pks, chain, null, null, tsaClient, 0, PdfSigner.CryptoStandard.CMS);
        }

        long newSize = Files.size(destPath);
        if (newSize <= originalSize) {
            throw new IllegalStateException("Destination PDF did not grow after signing; aborting to avoid overwriting");
        }

        if (signatureContext == null) {
            throw new IllegalStateException("Signature field context unavailable after signing");
        }
        validateSignedOutput(destPath, sigName, initialSignatureCount,
                signatureContext.getPageNumber(), new Rectangle(signatureContext.getRect()));
        System.out.println("[sign-row] Signature applied in append mode");
    }

    public static void certifyDocument(String src, String dest, String certPath, String password) throws Exception {
        String pkcs12 = certPath;
        char[] pwd = password != null ? password.toCharArray() : "123456".toCharArray();
        if (pkcs12 == null) {
            pkcs12 = DemoKeystoreUtil.createDemoP12().toAbsolutePath().toString();
            System.out.println("[certify] Generated demo PKCS#12 at " + pkcs12);
        }
        KeyStore ks = DemoKeystoreUtil.loadKeyStore(pkcs12, pwd);
        KeyStore.PrivateKeyEntry entry = DemoKeystoreUtil.firstPrivateKey(ks, pwd);
        PrivateKey privateKey = entry.getPrivateKey();
        X509Certificate[] chain = Arrays.stream(entry.getCertificateChain())
                .map(cert -> (X509Certificate) cert)
                .toArray(X509Certificate[]::new);

        try (PdfReader reader = new PdfReader(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            if (DocMDPUtil.hasDocMDP(signer.getDocument())) {
                throw new IllegalStateException("Document already has DocMDP certification");
            }
            DocMDPUtil.applyCertification(signer, privateKey, chain, DocMDPUtil.Permission.FORM_FILL_AND_SIGNATURES);
        }
    }

    private static SigningMode resolveMode(SigningMode requestedMode, PdfAcroForm form, String timeName, String textName,
                                           String nurseName, String sigName) {
        boolean fieldsPresent = form.getField(timeName) != null
                && form.getField(textName) != null
                && form.getField(nurseName) != null
                && form.getField(sigName) != null;
        if (requestedMode == SigningMode.AUTO) {
            return fieldsPresent ? SigningMode.TEMPLATE : SigningMode.INJECT;
        }
        if (requestedMode == SigningMode.TEMPLATE && !fieldsPresent) {
            throw new IllegalStateException("Expected template fields to exist for row");
        }
        return requestedMode;
    }

    private static PdfTextFormField resolveTextField(PdfDocument document, PdfAcroForm form, int pageNumber, Rectangle pageSize,
                                                     int row, String name, LayoutUtil.FieldSlot slot, boolean multiline,
                                                     SigningMode mode) {
        PdfFormField field = form.getField(name);
        if (field == null) {
            if (mode != SigningMode.INJECT) {
                throw new IllegalStateException("Field not found: " + name);
            }
            Rectangle rect = LayoutUtil.getFieldRect(pageSize, row, slot);
            PdfTextFormField created = multiline
                    ? PdfTextFormField.createMultilineText(document, rect, name, "")
                    : PdfTextFormField.createText(document, rect, name, "");
            created.setFontSize(12);
            if (!multiline) {
                created.setJustification(PdfFormField.ALIGN_CENTER);
            }
            form.addField(created, document.getPage(pageNumber));
            return created;
        }
        if (!(field instanceof PdfTextFormField)) {
            throw new IllegalStateException("Field is not a text field: " + name);
        }
        PdfTextFormField textField = (PdfTextFormField) field;
        if (multiline) {
            textField.setMultiline(true);
        }
        return textField;
    }

    private static SignatureFieldContext ensureSignatureField(PdfDocument document, PdfAcroForm acro, int pageNumber,
                                                              int row, String name, SigningMode mode) {
        PdfFormField field = acro.getField(name);
        Rectangle rect = LayoutUtil.sigRectForRow(document, pageNumber, row);
        PdfPage page = document.getPage(pageNumber);
        if (field == null) {
            if (mode != SigningMode.INJECT) {
                throw new IllegalStateException("Signature field " + name + " not found");
            }
            PdfFormField createdField = PdfFormField.createSignature(document, rect);
            createdField.setFieldName(name);
            PdfWidgetAnnotation widget = createdField.getWidgets().get(0);
            widget.setPage(page);
            int flags = PdfAnnotation.PRINT;
            widget.setFlags(flags);
            clearHiddenFlags(widget);
            acro.addField(createdField);
            page.addAnnotation(widget);
            return new SignatureFieldContext(createdField, widget, rect, pageNumber);
        }
        PdfName formType = field.getFormType();
        if (formType == null) {
            formType = field.getPdfObject().getAsName(PdfName.FT);
        }
        if (!PdfName.Sig.equals(formType)) {
            throw new IllegalStateException("Field is not a signature field: " + name);
        }
        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            throw new IllegalStateException("Signature field " + name + " has no widget annotation");
        }
        PdfWidgetAnnotation targetWidget = null;
        for (PdfWidgetAnnotation widget : widgets) {
            if (widget.getPage() == null) {
                continue;
            }
            int widgetPage = document.getPageNumber(widget.getPage());
            if (widgetPage == pageNumber) {
                targetWidget = widget;
                break;
            }
        }
        if (targetWidget == null) {
            throw new IllegalStateException("Signature field " + name + " is not placed on page " + pageNumber);
        }
        Rectangle widgetRect = targetWidget.getRectangle().toRectangle();
        if (widgetRect.getWidth() <= 0 || widgetRect.getHeight() <= 0) {
            throw new IllegalStateException("Signature widget for " + name + " has invalid rectangle");
        }
        Rectangle expectedRect = LayoutUtil.sigRectForRow(document, pageNumber, row);
        if (!SignatureDiagnostics.rectanglesSimilar(widgetRect, expectedRect)) {
            throw new IllegalStateException("Signature widget rectangle mismatch for " + name);
        }
        int flags = targetWidget.getFlags();
        if ((flags & PdfAnnotation.PRINT) == 0) {
            throw new IllegalStateException("Signature widget " + name + " is not printable (missing PRINT flag)");
        }
        if ((flags & (PdfAnnotation.HIDDEN | PdfAnnotation.INVISIBLE | PdfAnnotation.TOGGLE_NO_VIEW | PdfAnnotation.NO_VIEW)) != 0) {
            throw new IllegalStateException("Signature widget " + name + " is hidden or not viewable");
        }
        PdfWidgetUtil.ensureWidgetInAnnots(targetWidget.getPage(), targetWidget, name);
        return new SignatureFieldContext(field, targetWidget, widgetRect, pageNumber);
    }

    private static void clearHiddenFlags(PdfWidgetAnnotation widget) {
        int flags = widget.getFlags();
        flags &= ~PdfAnnotation.HIDDEN;
        flags &= ~PdfAnnotation.INVISIBLE;
        flags &= ~PdfAnnotation.TOGGLE_NO_VIEW;
        flags &= ~PdfAnnotation.NO_VIEW;
        widget.setFlags(flags | PdfAnnotation.PRINT);
    }

    private static PdfFont resolveAppearanceFont() {
        try {
            Path bundled = Path.of("fonts", "NotoSansCJKsc-Regular.otf");
            if (Files.exists(bundled)) {
                return PdfFontFactory.createFont(bundled.toAbsolutePath().toString(), PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            }
            return PdfFontFactory.createFont("NotoSansCJKsc-Regular.otf", PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            System.out.println("[sign-row] CJK font unavailable (" + e.getMessage() + "); falling back to Helvetica");
            try {
                return PdfFontFactory.createFont(StandardFonts.HELVETICA);
            } catch (Exception fallback) {
                throw new IllegalStateException("Unable to initialize fallback font", fallback);
            }
        }
    }

    private static final class SignatureFieldContext {
        private final PdfFormField field;
        private final PdfWidgetAnnotation widget;
        private final Rectangle rect;
        private final int pageNumber;

        private SignatureFieldContext(PdfFormField field, PdfWidgetAnnotation widget, Rectangle rect, int pageNumber) {
            this.field = field;
            this.widget = widget;
            this.rect = rect;
            this.pageNumber = pageNumber;
        }

        PdfFormField getField() {
            return field;
        }

        PdfWidgetAnnotation getWidget() {
            return widget;
        }

        Rectangle getRect() {
            return rect;
        }

        int getPageNumber() {
            return pageNumber;
        }
    }

    private static void validateSignedOutput(Path destPath, String sigName, int initialSignatureCount,
                                             int expectedPage, Rectangle expectedRect) throws Exception {
        requirePdfHeader(destPath);
        try (PdfDocument pdf = new PdfDocument(new PdfReader(destPath.toString()))) {
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                throw new IllegalStateException("No AcroForm found after signing");
            }
            SignatureUtil util = new SignatureUtil(pdf);
            List<String> names = util.getSignatureNames();
            if (names == null || names.isEmpty()) {
                throw new IllegalStateException("No field-bound signatures found (Adobe panel will be empty)");
            }
            if (initialSignatureCount >= 0 && names.size() != initialSignatureCount + 1) {
                throw new IllegalStateException("Expected signature count to increase by 1 (" + initialSignatureCount
                        + " -> " + (initialSignatureCount + 1) + ") but got " + names.size());
            }
            if (!names.contains(sigName)) {
                throw new IllegalStateException("Expected signature name not found: " + sigName);
            }
            SignatureDiagnostics.SignatureCheckResult result =
                    SignatureDiagnostics.inspectSignature(destPath, pdf, util, acro, sigName);
            if (result.getPageNumber() != expectedPage) {
                throw new IllegalStateException("Signature widget for " + sigName + " expected on page "
                        + expectedPage + " but found on page " + result.getPageNumber());
            }
            if (!SignatureDiagnostics.rectanglesSimilar(result.getWidgetRect(), expectedRect)) {
                throw new IllegalStateException("Signature widget rectangle mismatch for " + sigName);
            }
        }
    }

    private static void requirePdfHeader(Path destPath) throws Exception {
        RandomAccessSourceFactory sourceFactory = new RandomAccessSourceFactory();
        RandomAccessFileOrArray raf = null;
        try {
            IRandomAccessSource source = sourceFactory.createBestSource(destPath.toString());
            raf = new RandomAccessFileOrArray(source);
            byte[] head = new byte[8];
            raf.readFully(head, 0, 8);
            String headStr = new String(head, java.nio.charset.StandardCharsets.US_ASCII);
            if (!headStr.startsWith("%PDF-")) {
                throw new IllegalStateException("PDF header is not at byte 0 (BOM or stray bytes). Adobe may ignore signatures.");
            }
        } finally {
            if (raf != null) {
                raf.close();
            }
        }
    }
}
