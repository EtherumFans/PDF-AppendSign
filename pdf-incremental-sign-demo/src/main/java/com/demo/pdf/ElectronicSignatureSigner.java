package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.security.PrivateKeySignature;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;
import com.itextpdf.text.pdf.security.TSAClient;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.Objects;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Visible signature helper based on iText 5.
 */
public final class ElectronicSignatureSigner {

    private ElectronicSignatureSigner() {
    }

    public static final class Params {
        private String source;
        private String destination;
        private String pkcs12Path;
        private String password;
        private String fieldName = "sig_electronic";
        private int page = 1;
        private float x = 72f;
        private float y = 72f;
        private float width = 180f;
        private float height = 72f;
        private String signerName;
        private String reason = "电子签名";
        private String location = "Ward";
        private String contact = "signer@example.com";
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

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            if (fieldName != null && !fieldName.isBlank()) {
                this.fieldName = fieldName;
            }
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
    }

    public static void sign(Params params) throws Exception {
        Objects.requireNonNull(params, "params");
        DemoKeystoreUtil.ensureProvider();
        SigningSupport.SigningContext ctx = SigningSupport.resolve(params.getPkcs12Path(), params.getPassword());

        if (params.getSource() == null || params.getDestination() == null) {
            throw new IllegalArgumentException("Source and destination must be provided");
        }
        Path sourcePath = Path.of(params.getSource()).toAbsolutePath();
        if (Files.notExists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("Source PDF not found: " + sourcePath);
        }
        Path destinationPath = Path.of(params.getDestination()).toAbsolutePath();
        ensureParentDir(destinationPath);

        PdfReader reader = new PdfReader(sourcePath.toString());
        try (FileOutputStream os = new FileOutputStream(destinationPath.toString())) {
            PdfStamper stamper = PdfStamper.createSignature(reader, os, '\0', null, true);
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setReason(params.getReason());
            appearance.setLocation(params.getLocation());
            appearance.setContact(params.getContact());
            Calendar signTime = Calendar.getInstance();
            appearance.setSignDate(signTime);
            Rectangle rect = new Rectangle(params.getX(), params.getY(), params.getX() + params.getWidth(), params.getY() + params.getHeight());
            appearance.setVisibleSignature(rect, params.getPage(), params.getFieldName());

            StringBuilder layerText = new StringBuilder();
            if (params.getSignerName() != null && !params.getSignerName().isBlank()) {
                layerText.append(params.getSignerName()).append('\n');
            }
            layerText.append("签署时间: ").append(signTime.getTime());
            appearance.setLayer2Text(layerText.toString());

            ExternalDigest digest = new BouncyCastleDigest();
            ExternalSignature signature = new PrivateKeySignature(ctx.privateKey(), "SHA256", BouncyCastleProvider.PROVIDER_NAME);
            Certificate[] chain = ctx.chain();
            TSAClient tsaClient = null;
            if (params.getTsaUrl() != null && !params.getTsaUrl().isBlank()) {
                tsaClient = new TSAClientBouncyCastle(params.getTsaUrl());
            }
            MakeSignature.signDetached(appearance, digest, signature, chain, null, null, tsaClient, 0, MakeSignature.CryptoStandard.CMS);
            stamper.close();
        } finally {
            reader.close();
        }
    }

    private static void ensureParentDir(Path dest) throws Exception {
        Path parent = dest.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
