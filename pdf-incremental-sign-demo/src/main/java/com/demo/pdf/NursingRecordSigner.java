package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import com.itextpdf.signatures.TSAClientBouncyCastle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;

public final class NursingRecordSigner {

    private NursingRecordSigner() {
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
        private String lang;

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

        public String getLang() {
            return lang;
        }

        public void setLang(String lang) {
            this.lang = lang;
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
        Certificate[] chain = entry.getCertificateChain();

        try (PdfReader reader = new PdfReader(params.getSource());
             FileOutputStream fos = new FileOutputStream(params.getDestination())) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties().useAppendMode());
            PdfAcroForm form = PdfAcroForm.getAcroForm(signer.getDocument(), true);

            String prefix = "row" + params.getRow();
            List<String> fieldsToLock = Arrays.asList(prefix + ".time", prefix + ".text", prefix + ".nurse");
            System.out.println("[sign-row] Filling fields: " + fieldsToLock);
            form.getField(prefix + ".time").setValue(params.getTimeValue());
            form.getField(prefix + ".text").setValue(params.getTextValue());
            form.getField(prefix + ".nurse").setValue(params.getNurse());
            for (String fName : fieldsToLock) {
                PdfFormField field = form.getField(fName);
                if (field == null) {
                    throw new IllegalStateException("Field not found: " + fName);
                }
                field.setReadOnly(true);
            }

            String sigFieldName = "sig_row_" + params.getRow();
            PdfFormField field = form.getField(sigFieldName);
            if (!(field instanceof PdfSignatureFormField)) {
                throw new IllegalStateException("Signature field not found: " + sigFieldName);
            }
            PdfSignatureFormField sigField = (PdfSignatureFormField) field;
            PdfDictionary lock = new PdfDictionary();
            lock.put(PdfName.Type, PdfName.SigFieldLock);
            lock.put(PdfName.Action, PdfName.Include);
            PdfArray lockedFields = new PdfArray();
            for (String fName : fieldsToLock) {
                lockedFields.add(new PdfString(fName));
            }
            lock.put(PdfName.Fields, lockedFields);
            sigField.getPdfObject().put(PdfName.Lock, lock);

            signer.setFieldName(sigFieldName);
            Rectangle rect = sigField.getWidgets().get(0).getRectangle().toRectangle();
            int pageNumber = signer.getDocument().getPageNumber(sigField.getWidgets().get(0).getPage());
            signer.getSignatureAppearance()
                    .setReuseAppearance(false)
                    .setPageRect(rect)
                    .setPageNumber(pageNumber)
                    .setReason("Nursing note " + params.getTimeValue())
                    .setLocation("Ward A")
                    .setLayer2Text(String.format("护士: %s\n时间: %s\n事由: Nursing note %s",
                            params.getNurse(), params.getTimeValue(), params.getTimeValue()));

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
}
