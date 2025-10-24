package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDate;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
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
import com.itextpdf.signatures.PdfSignature;
import com.itextpdf.signatures.TSAClientBouncyCastle;
import com.itextpdf.forms.PdfSigFieldLock;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import java.util.Calendar;
import java.util.TimeZone;

public final class NursingRecordSigner {

    private static final String DEFAULT_CONTACT_INFO = "nurse-signer@example.com";
    private static final String DEFAULT_LOCATION = "Ward A";

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
        PdfSanityUtil.requireVersionAtLeast(srcPath, "1.6");

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

        Path parent = destPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        PdfDocument document = null;
        try (PdfReader reader = new PdfReader(params.getSource());
             OutputStream fos = new BufferedOutputStream(Files.newOutputStream(destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE))) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            document = signer.getDocument();
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
            String signerName = params.getNurse() == null || params.getNurse().isBlank()
                    ? "Signer"
                    : params.getNurse();
            String reason = "Nursing note " + params.getTimeValue();
            String location = DEFAULT_LOCATION;
            appearance.setReason(reason);
            appearance.setLocation(location);
            appearance.setContact(DEFAULT_CONTACT_INFO);
            Calendar signDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            if (keyMaterial.x509Chain.length > 0) {
                appearance.setCertificate(keyMaterial.x509Chain[0]);
            }
            String layer2 = String.format("护士: %s\n时间: %s\n事由: %s",
                    signerName, params.getTimeValue(), reason);
            PdfFont font = resolveAppearanceFont();
            appearance.setLayer2Font(font);
            appearance.setLayer2Text(layer2);

            signer.setSignDate(signDate);
            signer.setSignatureEvent(signature -> configureSignatureDictionary(signature, signerName, reason, location, signDate));

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
            assertWidgetPrintable(signatureContext.getField());
            signer.signDetached(digest, pks, keyMaterial.x509Chain, null, null, tsaClient, 0, PdfSigner.CryptoStandard.CMS);
        } finally {
            if (document != null && !document.isClosed()) {
                document.close();
            }
        }

        long newSize = Files.size(destPath);
        if (newSize <= originalSize) {
            throw new IllegalStateException("Destination PDF did not grow after signing; aborting to avoid overwriting");
        }

        if (signatureContext == null) {
            throw new IllegalStateException("Signature field context unavailable after signing");
        }

        PostSignValidator.validate(destPath.toString(), sigName);
        PostSignValidator.strictTailCheck(destPath);
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

        Path srcPath = Path.of(src);
        PdfSanityUtil.requireHeader(srcPath);
        PdfSanityUtil.requireVersionAtLeast(srcPath, "1.6");
        Path destPath = Path.of(dest);
        Path parent = destPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        PdfDocument document = null;
        try (PdfReader reader = new PdfReader(src);
             OutputStream fos = new BufferedOutputStream(Files.newOutputStream(destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE))) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            document = signer.getDocument();
            if (DocMDPUtil.hasDocMDP(document)) {
                throw new IllegalStateException("Document already has DocMDP certification");
            }
            DocMDPUtil.applyCertification(signer, keyMaterial.key, keyMaterial.x509Chain, DocMDPUtil.Permission.FORM_FILL_AND_SIGNATURES);
        } finally {
            if (document != null && !document.isClosed()) {
                document.close();
            }
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

    private static void configureSignatureDictionary(PdfSignature signature, String signerName,
                                                     String reason, String location, Calendar signDate) {
        if (signature == null) {
            return;
        }
        signature.put(PdfName.Type, PdfName.Sig);
        signature.put(PdfName.Filter, PdfName.Adobe_PPKLite);
        signature.put(PdfName.SubFilter, PdfName.Adbe_pkcs7_detached);
        if (signDate != null) {
            signature.put(PdfName.M, new PdfDate(signDate).getPdfObject());
        }
        if (signerName != null && !signerName.isBlank()) {
            signature.put(PdfName.Name, new PdfString(signerName));
        }
        if (reason != null && !reason.isBlank()) {
            signature.put(PdfName.Reason, new PdfString(reason));
        }
        if (location != null && !location.isBlank()) {
            signature.put(PdfName.Location, new PdfString(location));
        }
        signature.put(PdfName.ContactInfo, new PdfString(DEFAULT_CONTACT_INFO));
    }

    private static SignatureFieldContext ensureSignatureField(PdfDocument document, PdfAcroForm acro, int pageNumber,
                                                              int row, String name, SigningMode mode) {
        Rectangle rect = LayoutUtil.sigRectForRow(document, pageNumber, row);
        PdfPage page = document.getPage(pageNumber);
        PdfFormField existingField = acro.getField(name);
        PdfSignatureFormField signatureField;
        if (existingField == null) {
            if (mode != SigningMode.INJECT) {
                throw new IllegalStateException("Signature field " + name + " not found");
            }
            signatureField = PdfFormField.createSignature(document, rect);
            signatureField.setFieldName(name);
            acro.addField(signatureField, page);
        } else {
            if (!(existingField instanceof PdfSignatureFormField)) {
                throw new IllegalStateException("Field is not a signature field: " + name);
            }
            signatureField = (PdfSignatureFormField) existingField;
        }

        PdfWidgetAnnotation widget = FormUtil.ensurePrintableSignatureWidget(signatureField, page, rect);
        PdfWidgetUtil.ensureWidgetInAnnots(page, widget, name);

        Rectangle normalizedRect = widget.getRectangle().toRectangle();
        if (!SignatureDiagnostics.rectanglesSimilar(normalizedRect, rect)) {
            throw new IllegalStateException("Signature widget rectangle mismatch for " + name);
        }
        return new SignatureFieldContext(signatureField, widget, new Rectangle(rect), pageNumber);
    }

    private static void assertWidgetPrintable(PdfSignatureFormField sig) {
        if (sig == null) {
            throw new IllegalArgumentException("Signature field must not be null");
        }
        List<PdfWidgetAnnotation> widgets = sig.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            throw new IllegalStateException("Signature field " + sig.getFieldName() + " has no widget annotation");
        }
        PdfWidgetAnnotation widget = widgets.get(0);
        int flags = widget.getFlags();
        if ((flags & PdfAnnotation.PRINT) == 0) {
            throw new IllegalStateException("Signature widget " + sig.getFieldName() + " is not printable (PRINT flag missing)");
        }
        if ((flags & (PdfAnnotation.INVISIBLE | PdfAnnotation.HIDDEN | PdfAnnotation.NO_VIEW | PdfAnnotation.TOGGLE_NO_VIEW)) != 0) {
            throw new IllegalStateException("Signature widget " + sig.getFieldName() + " is hidden (INVISIBLE/HIDDEN/NOVIEW)");
        }
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
        if (!"RSA".equalsIgnoreCase(key.getAlgorithm())) {
            throw new IllegalStateException("Signing key algorithm must be RSA for Adobe compatibility; got: " + key.getAlgorithm());
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
            if (x509Chain[i].getVersion() < 3) {
                throw new IllegalStateException("Certificate " + x509Chain[i].getSubjectX500Principal() + " is not X.509 v3");
            }
        }
        String sigAlg = x509Chain[0].getSigAlgName();
        String normalizedAlg = sigAlg != null ? sigAlg.toUpperCase(Locale.ROOT) : "";
        if (!normalizedAlg.contains("SHA256") || !normalizedAlg.contains("RSA")) {
            throw new IllegalStateException("Signing certificate must use SHA256withRSA; got: " + sigAlg);
        }
        return new KeyMaterial(key, x509Chain);
    }

    private static final class KeyMaterial {
        private final PrivateKey key;
        private final X509Certificate[] x509Chain;

        private KeyMaterial(PrivateKey key, X509Certificate[] x509Chain) {
            this.key = key;
            this.x509Chain = x509Chain;
        }
    }

    private static final class SignatureFieldContext {
        private final PdfSignatureFormField field;
        private final PdfWidgetAnnotation widget;
        private final Rectangle rect;
        private final int pageNumber;

        private SignatureFieldContext(PdfSignatureFormField field, PdfWidgetAnnotation widget, Rectangle rect, int pageNumber) {
            this.field = field;
            this.widget = widget;
            this.rect = rect;
            this.pageNumber = pageNumber;
        }

        PdfSignatureFormField getField() {
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
            if (!result.isAdobeVisibleMinimalStructure()) {
                throw new IllegalStateException("Signature minimal structure invalid after signing: "
                        + String.join("; ", result.getAdobeVisibilityIssues()));
            }
            if (result.getWidgetRect() == null) {
                throw new IllegalStateException("Signature widget rectangle unavailable for " + sigName);
            }
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
