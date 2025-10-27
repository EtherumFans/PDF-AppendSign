package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.BaseFont;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Append a visible electronic signature using iText 5 APIs.
 */
public final class ElectronicSignatureSigner {

    private static final TimeZone SIGN_TIMEZONE = TimeZone.getTimeZone("UTC");

    private ElectronicSignatureSigner() {
    }

    public static class Params {
        private String source;
        private String destination;
        private String pkcs12Path;
        private String password;
        private int page = 1;
        private float x;
        private float y;
        private float width = 180f;
        private float height = 72f;
        private String fieldName = "sig_electronic";
        private String signerName;
        private String reason = "电子签名";
        private String location = "Ward A";
        private String contact = "nurse-signer@example.com";
        private java.nio.file.Path cjkFontPath;
        private boolean debugFonts;
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

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }

        public float getWidth() {
            return width;
        }

        public void setWidth(float width) {
            this.width = width;
        }

        public float getHeight() {
            return height;
        }

        public void setHeight(float height) {
            this.height = height;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getSignerName() {
            return signerName;
        }

        public void setSignerName(String signerName) {
            this.signerName = signerName;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getContact() {
            return contact;
        }

        public void setContact(String contact) {
            this.contact = contact;
        }

        public java.nio.file.Path getCjkFontPath() {
            return cjkFontPath;
        }

        public void setCjkFontPath(java.nio.file.Path cjkFontPath) {
            this.cjkFontPath = cjkFontPath;
        }

        public boolean isDebugFonts() {
            return debugFonts;
        }

        public void setDebugFonts(boolean debugFonts) {
            this.debugFonts = debugFonts;
        }

        public String getTsaUrl() {
            return tsaUrl;
        }

        public void setTsaUrl(String tsaUrl) {
            this.tsaUrl = tsaUrl;
        }
    }

    public static void sign(Params params) throws Exception {
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
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setReason(params.getReason());
            appearance.setLocation(params.getLocation());
            appearance.setContact(params.getContact());
            appearance.setSignDate(Calendar.getInstance(SIGN_TIMEZONE));
            if (material.chain.length > 0) {
                appearance.setCertificate(material.chain[0]);
            }
            float llx = params.getX();
            float lly = params.getY();
            float urx = llx + params.getWidth();
            float ury = lly + params.getHeight();
            appearance.setVisibleSignature(new com.itextpdf.text.Rectangle(llx, lly, urx, ury), params.getPage(), params.getFieldName());

            BaseFont layerFont = resolveFont(params.getCjkFontPath());
            if (layerFont != null) {
                appearance.setLayer2Font(new Font(layerFont, 12f));
            }
            String signer = params.getSignerName() == null || params.getSignerName().isBlank()
                    ? "Signer"
                    : params.getSignerName();
            String layerText = String.format("签署人: %s\n理由: %s\n地点: %s",
                    signer,
                    params.getReason() == null ? "" : params.getReason(),
                    params.getLocation() == null ? "" : params.getLocation());
            appearance.setLayer2Text(layerText);

            ExternalDigest digest = new BouncyCastleDigest();
            ExternalSignature signature = new PrivateKeySignature(material.key, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            TSAClientBouncyCastle tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }
            MakeSignature.signDetached(appearance, digest, signature, material.chain, null, null, tsaClient, 0, MakeSignature.CryptoStandard.CMS);
        }
    }

    private static BaseFont resolveFont(java.nio.file.Path cjkFont) {
        if (cjkFont == null) {
            return null;
        }
        try {
            if (Files.isRegularFile(cjkFont)) {
                return BaseFont.createFont(cjkFont.toAbsolutePath().toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        } catch (Exception e) {
            System.err.println("[sign-electronic] Unable to load custom font: " + e.getMessage());
        }
        return null;
    }

    private static KeyMaterial loadKeyMaterial(String pkcs12Path, String password) throws Exception {
        char[] pwd = password != null ? password.toCharArray() : "123456".toCharArray();
        String effective = pkcs12Path;
        if (effective == null) {
            effective = DemoKeystoreUtil.createDemoP12().toAbsolutePath().toString();
            System.out.println("[sign-electronic] Using generated demo PKCS#12: " + effective);
        }
        KeyStore ks = DemoKeystoreUtil.loadKeyStore(effective, pwd);
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

    private static final class KeyMaterial {
        private final PrivateKey key;
        private final X509Certificate[] chain;

        private KeyMaterial(PrivateKey key, X509Certificate[] chain) {
            this.key = key;
            this.chain = chain;
        }
    }
}
