package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.DigestAlgorithms;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PrivateKeySignature;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Simplified nursing record signer using iText 5.
 */
public final class NursingRecordSigner {

    private static final String DEFAULT_CONTACT_INFO = "nurse-signer@example.com";
    private static final String DEFAULT_LOCATION = "Ward A";
    private static final TimeZone SIGN_TIMEZONE = TimeZone.getTimeZone("UTC");

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
    }

    public static void signRow(SignParams params) throws Exception {
        if (params.getRow() < 1) {
            throw new IllegalArgumentException("Row index must start from 1");
        }
        Path src = Path.of(params.getSource());
        Path dest = Path.of(params.getDestination());
        if (src.toAbsolutePath().equals(dest.toAbsolutePath())) {
            throw new IllegalArgumentException("Source and destination must differ to avoid truncation");
        }
        Path parent = dest.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        KeyMaterial material = loadKeyMaterial(params.getPkcs12Path(), params.getPassword());
        DocMDPUtil.ensureProvider();

        try (PdfReader reader = new PdfReader(params.getSource());
             FileOutputStream os = new FileOutputStream(dest.toFile())) {
            PdfStamper stamper = PdfStamper.createSignature(reader, os, '\0', null, true);
            AcroFields form = stamper.getAcroFields();
            String prefix = NursingRecordTemplate.FIELD_SIGN_PREFIX + params.getRow();
            String timeName = NursingRecordTemplate.FIELD_TIME_PREFIX + params.getRow();
            String textName = NursingRecordTemplate.FIELD_CONTENT_PREFIX + params.getRow();
            String nurseName = NursingRecordTemplate.FIELD_NURSE_PREFIX + params.getRow();

            setFieldIfPresent(form, timeName, params.getTimeValue());
            setFieldIfPresent(form, textName, params.getTextValue());
            setFieldIfPresent(form, nurseName, params.getNurse());
            setReadOnly(form, timeName);
            setReadOnly(form, textName);
            setReadOnly(form, nurseName);

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setVisibleSignature(prefix);
            appearance.setReason("Nursing note " + safe(params.getTimeValue()));
            appearance.setLocation(DEFAULT_LOCATION);
            appearance.setContact(DEFAULT_CONTACT_INFO);
            appearance.setSignDate(Calendar.getInstance(SIGN_TIMEZONE));
            if (material.chain.length > 0) {
                appearance.setCertificate(material.chain[0]);
            }
            String signerName = params.getNurse() == null || params.getNurse().isBlank() ? "Signer" : params.getNurse();
            appearance.setLayer2Text(String.format("护士: %s\n时间: %s\n内容: %s",
                    signerName,
                    safe(params.getTimeValue()),
                    abbreviate(safe(params.getTextValue()))));

            ExternalDigest digest = new BouncyCastleDigest();
            ExternalSignature signature = new PrivateKeySignature(material.key, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            TSAClientBouncyCastle tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }
            MakeSignature.signDetached(appearance, digest, signature, material.chain, null, null, tsaClient, 0, MakeSignature.CryptoStandard.CMS);
        }
    }

    public static void certifyDocument(String src, String dest, String certPath, String password) throws Exception {
        Path source = Path.of(src);
        Path destination = Path.of(dest);
        if (source.toAbsolutePath().equals(destination.toAbsolutePath())) {
            throw new IllegalArgumentException("Source and destination must differ for certification");
        }
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        KeyMaterial material = loadKeyMaterial(certPath, password);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        Path temp = Files.createTempFile("docmdp", ".pdf");
        try {
            Files.copy(destination, temp, StandardCopyOption.REPLACE_EXISTING);
            DocMDPUtil.certify(temp.toString(), destination.toString(), material.key, material.chain,
                    PdfSignatureAppearance.CERTIFIED_FORM_FILLING_AND_ANNOTATIONS);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void setFieldIfPresent(AcroFields form, String name, String value) throws DocumentException, IOException {
        if (form.getFieldItem(name) != null && value != null) {
            form.setField(name, value);
        }
    }

    private static void setReadOnly(AcroFields form, String name) {
        if (form.getFieldItem(name) != null) {
            form.setFieldProperty(name, "setfflags", PdfFormField.FF_READ_ONLY, null);
        }
    }

    private static KeyMaterial loadKeyMaterial(String pkcs12Path, String password) throws Exception {
        char[] pwd = password != null ? password.toCharArray() : "123456".toCharArray();
        String effectivePath = pkcs12Path;
        if (effectivePath == null) {
            effectivePath = DemoKeystoreUtil.createDemoP12().toAbsolutePath().toString();
            System.out.println("[sign-row] Using generated demo PKCS#12: " + effectivePath);
        }
        KeyStore ks = DemoKeystoreUtil.loadKeyStore(effectivePath, pwd);
        KeyStore.PrivateKeyEntry entry = DemoKeystoreUtil.firstPrivateKey(ks, pwd);
        PrivateKey key = entry.getPrivateKey();
        X509Certificate[] chain = Arrays.stream(entry.getCertificateChain())
                .map(cert -> (X509Certificate) cert)
                .toArray(X509Certificate[]::new);
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        return new KeyMaterial(key, chain);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String abbreviate(String content) {
        if (content.length() <= 60) {
            return content;
        }
        return content.substring(0, 57) + "...";
    }

    private static final class KeyMaterial {
        private final PrivateKey key;
        private final X509Certificate[] chain;

        private KeyMaterial(PrivateKey key, X509Certificate[] chain) {
            this.key = key;
            this.chain = chain;
        }
    }
}
