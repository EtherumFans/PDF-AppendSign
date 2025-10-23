package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
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
import com.itextpdf.forms.PdfSigFieldLock;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
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
        KeyMaterial keyMaterial = loadKeyMaterial(pkcs12Path, password);

        String sigName = "sig_row_" + params.getRow();

        SignatureFieldContext signatureContext = null;

        try (PdfReader reader = new PdfReader(params.getSource());
             FileOutputStream fos = new FileOutputStream(params.getDestination())) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            PdfDocument document = signer.getDocument();
            PdfAcroForm acro = PdfAcroForm.getAcroForm(document, true);
            FormUtil.ensureNeedAppearances(acro);
            acro.setSignatureFlags(PdfAcroForm.SIGNATURE_EXIST | PdfAcroForm.APPEND_ONLY);

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

            PdfSigFieldLock lock = new PdfSigFieldLock()
                    .setFieldLock(PdfSigFieldLock.LockAction.INCLUDE, timeName, textName, nurseName);
            signatureContext.getField().put(PdfName.Lock, lock.getPdfObject());

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

            if (keyMaterial.x509Chain.length > 0) {
                System.out.println("[sign-row] Signer cert subject: "
                        + keyMaterial.x509Chain[0].getSubjectX500Principal());
                System.out.println("[sign-row] Certificate chain length: " + keyMaterial.x509Chain.length);
            }

            IExternalSignature pks = new PrivateKeySignature(keyMaterial.key, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            IExternalDigest digest = new BouncyCastleDigest();
            TSAClientBouncyCastle tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }
            signer.signDetached(digest, pks, keyMaterial.certificateChain, null, null, tsaClient, 0, PdfSigner.CryptoStandard.CMS);
        }

        long newSize = Files.size(destPath);
        if (newSize <= originalSize) {
            throw new IllegalStateException("Destination PDF did not grow after signing; aborting to avoid overwriting");
        }

        if (signatureContext == null) {
            throw new IllegalStateException("Signature field context unavailable after signing");
        }

        PostSignValidator.validate(destPath.toString(), sigName);
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
        KeyMaterial keyMaterial = loadKeyMaterial(pkcs12, pwd);

        try (PdfReader reader = new PdfReader(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            if (DocMDPUtil.hasDocMDP(signer.getDocument())) {
                throw new IllegalStateException("Document already has DocMDP certification");
            }
            DocMDPUtil.applyCertification(signer, keyMaterial.key, keyMaterial.x509Chain, DocMDPUtil.Permission.FORM_FILL_AND_SIGNATURES);
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
            attachWidgetToPage(page, widget, name);
            PdfNumber flagNumber = new PdfNumber(widget.getFlags());
            widget.getPdfObject().put(PdfName.F, flagNumber);
            acro.addField(createdField);
            ensureWidgetRegistered(page, widget);
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
        attachWidgetToPage(page, targetWidget, name);
        ensureWidgetRegistered(page, targetWidget);
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
        widget.resetFlag(PdfAnnotation.HIDDEN);
        widget.resetFlag(PdfAnnotation.INVISIBLE);
        widget.resetFlag(PdfAnnotation.TOGGLE_NO_VIEW);
        widget.resetFlag(PdfAnnotation.NO_VIEW);
        widget.setFlag(PdfAnnotation.PRINT);
    }

    private static void attachWidgetToPage(PdfPage page, PdfWidgetAnnotation widget, String fieldName) {
        if (page == null) {
            throw new IllegalStateException("Cannot attach widget for " + fieldName + " to null page");
        }
        clearHiddenFlags(widget);
        widget.setPage(page);
        PdfDictionary widgetObject = widget.getPdfObject();
        int flags = widget.getFlags() | PdfAnnotation.PRINT;
        widget.setFlags(flags);
        widgetObject.put(PdfName.P, page.getPdfObject());
        widgetObject.put(PdfName.F, new PdfNumber(flags));
    }

    private static void ensureWidgetRegistered(PdfPage page, PdfWidgetAnnotation widget) {
        PdfDictionary pageObject = page.getPdfObject();
        PdfArray annots = pageObject.getAsArray(PdfName.Annots);
        if (annots == null) {
            annots = new PdfArray();
            pageObject.put(PdfName.Annots, annots);
        }
        PdfDictionary widgetObject = widget.getPdfObject();
        if (!containsAnnotationReference(annots, widgetObject)) {
            annots.add(widgetObject);
        }
    }

    private static boolean containsAnnotationReference(PdfArray annots, PdfDictionary widgetObject) {
        for (int i = 0; i < annots.size(); i++) {
            PdfObject candidate = annots.get(i);
            if (candidate == null) {
                continue;
            }
            if (candidate.getIndirectReference() != null && widgetObject.getIndirectReference() != null
                    && candidate.getIndirectReference().equals(widgetObject.getIndirectReference())) {
                return true;
            }
            if (candidate.equals(widgetObject)) {
                return true;
            }
        }
        return false;
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

    private static KeyMaterial loadKeyMaterial(String pkcs12Path, char[] password) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(Path.of(pkcs12Path))) {
            ks.load(is, password);
        }
        String alias = Collections.list(ks.aliases()).stream()
                .filter(a -> {
                    try {
                        return ks.isKeyEntry(a);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No key entry in PKCS#12: " + pkcs12Path));
        PrivateKey key = (PrivateKey) ks.getKey(alias, password);
        if (key == null) {
            throw new IllegalStateException("PrivateKey is null for alias: " + alias);
        }
        Certificate[] chain = ks.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            throw new IllegalStateException("Certificate chain is empty (cannot embed signer cert).");
        }
        X509Certificate[] x509Chain = new X509Certificate[chain.length];
        for (int i = 0; i < chain.length; i++) {
            if (!(chain[i] instanceof X509Certificate)) {
                throw new IllegalStateException("Certificate chain entry is not X509Certificate: index " + i);
            }
            x509Chain[i] = (X509Certificate) chain[i];
        }
        return new KeyMaterial(key, chain.clone(), x509Chain);
    }

    private static final class KeyMaterial {
        private final PrivateKey key;
        private final Certificate[] certificateChain;
        private final X509Certificate[] x509Chain;

        private KeyMaterial(PrivateKey key, Certificate[] certificateChain, X509Certificate[] x509Chain) {
            this.key = key;
            this.certificateChain = certificateChain;
            this.x509Chain = x509Chain;
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
        try (PdfDocument pdf = new PdfDocument(new PdfReader(destPath.toString()))) {
            SignatureUtil util = new SignatureUtil(pdf);
            List<String> names = util.getSignatureNames();
            if (names == null || names.isEmpty()) {
                throw new IllegalStateException("No field-bound signatures.");
            }
            if (!names.contains(sigName)) {
                throw new IllegalStateException("Missing expected signature: " + sigName);
            }
            if (initialSignatureCount >= 0 && names.size() != initialSignatureCount + 1) {
                throw new IllegalStateException("Expected signature count to increase by 1 (" + initialSignatureCount
                        + " -> " + (initialSignatureCount + 1) + ") but got " + names.size());
            }

            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
            if (acro == null) {
                throw new IllegalStateException("No AcroForm.");
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
}
