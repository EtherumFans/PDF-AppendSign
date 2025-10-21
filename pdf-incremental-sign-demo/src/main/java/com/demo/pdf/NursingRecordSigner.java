package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import com.itextpdf.signatures.TSAClientBouncyCastle;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;

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

        try (PdfReader reader = new PdfReader(params.getSource());
             FileOutputStream fos = new FileOutputStream(params.getDestination())) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            PdfDocument document = signer.getDocument();
            PdfAcroForm form = FormUtil.ensureAcroForm(document);

            String prefix = "row" + params.getRow();
            String timeName = prefix + ".time";
            String textName = prefix + ".text";
            String nurseName = prefix + ".nurse";
            String sigName = "sig_row_" + params.getRow();

            SigningMode effectiveMode = resolveMode(params.getMode(), form, timeName, textName, nurseName, sigName);
            System.out.println("[sign-row] Effective mode: " + effectiveMode);

            if (params.getPage() < 1 || params.getPage() > document.getNumberOfPages()) {
                throw new IllegalArgumentException("Page " + params.getPage() + " is out of bounds");
            }
            Rectangle pageSize = document.getPage(params.getPage()).getPageSize();

            PdfTextFormField timeField = resolveTextField(document, form, params.getPage(), pageSize, params.getRow(), timeName,
                    LayoutUtil.FieldSlot.TIME, false, effectiveMode);
            PdfTextFormField textField = resolveTextField(document, form, params.getPage(), pageSize, params.getRow(), textName,
                    LayoutUtil.FieldSlot.TEXT, true, effectiveMode);
            PdfTextFormField nurseField = resolveTextField(document, form, params.getPage(), pageSize, params.getRow(), nurseName,
                    LayoutUtil.FieldSlot.NURSE, false, effectiveMode);
            PdfSignatureFormField sigField = resolveSignatureField(document, form, params.getPage(), pageSize, params.getRow(), sigName,
                    effectiveMode);

            timeField.setValue(params.getTimeValue());
            textField.setValue(params.getTextValue());
            nurseField.setValue(params.getNurse());
            timeField.setReadOnly(true);
            textField.setReadOnly(true);
            nurseField.setReadOnly(true);

            FieldLockUtil.applyIncludeLock(sigField, timeName, textName, nurseName);

            if (effectiveMode == SigningMode.INJECT && params.isCertifyOnFirstInject() && !DocMDPUtil.hasDocMDP(document)) {
                signer.setCertificationLevel(DocMDPUtil.Permission.FORM_FILL_AND_SIGNATURES.getCertificationLevel());
            }

            signer.setFieldName(sigName);
            PdfWidgetAnnotation widget = sigField.getWidgets().get(0);
            Rectangle signatureRect = widget.getRectangle().toRectangle();
            int sigPageNumber = document.getPageNumber(widget.getPage());
            PdfSignatureAppearance appearance = signer.getSignatureAppearance();
            appearance.setReuseAppearance(false);
            appearance.setPageRect(signatureRect);
            appearance.setPageNumber(sigPageNumber);
            appearance.setReason("Nursing note " + params.getTimeValue());
            appearance.setLocation("Ward A");
            String layer2 = String.format("护士: %s\n时间: %s\n事由: Nursing note %s",
                    params.getNurse(), params.getTimeValue(), params.getTimeValue());
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            appearance.setLayer2Font(font);
            appearance.setLayer2Text(layer2);

            IExternalSignature pks = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            IExternalDigest digest = new BouncyCastleDigest();
            TSAClientBouncyCastle tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }
            signer.signDetached(digest, pks, chain, null, null, tsaClient, 0, PdfSigner.CryptoStandard.CADES);
        }
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

    private static PdfSignatureFormField resolveSignatureField(PdfDocument document, PdfAcroForm form, int pageNumber,
                                                                Rectangle pageSize, int row, String name, SigningMode mode) {
        PdfFormField field = form.getField(name);
        if (field == null) {
            if (mode != SigningMode.INJECT) {
                throw new IllegalStateException("Signature field not found: " + name);
            }
            Rectangle rect = LayoutUtil.getFieldRect(pageSize, row, LayoutUtil.FieldSlot.SIGNATURE);
            PdfSignatureFormField created = PdfSignatureFormField.createSignature(document, rect);
            created.setFieldName(name);
            form.addField(created, document.getPage(pageNumber));
            return created;
        }
        if (!(field instanceof PdfSignatureFormField)) {
            throw new IllegalStateException("Field is not a signature field: " + name);
        }
        return (PdfSignatureFormField) field;
    }
}
