package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PrivateKeySignature;
import com.itextpdf.text.pdf.security.TSAClient;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;

import java.io.FileOutputStream;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.Objects;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Simple helper that fills a nursing record row and signs the corresponding signature field.
 */
public final class NursingRecordSigner {

    private NursingRecordSigner() {
    }

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
    }

    public static void signRow(SignParams params) throws Exception {
        Objects.requireNonNull(params, "params");
        if (params.getRow() < 1) {
            throw new IllegalArgumentException("Row index must be >= 1");
        }
        DemoKeystoreUtil.ensureProvider();

        SigningSupport.SigningContext ctx = SigningSupport.resolve(params.getPkcs12Path(), params.getPassword());

        PdfReader reader = new PdfReader(params.getSource());
        try (FileOutputStream os = new FileOutputStream(params.getDestination())) {
            PdfStamper stamper = PdfStamper.createSignature(reader, os, '\0', null, true);
            AcroFields fields = stamper.getAcroFields();
            String timeField = NursingRecordTemplate.timeFieldName(params.getRow());
            String contentField = NursingRecordTemplate.contentFieldName(params.getRow());
            String nurseField = NursingRecordTemplate.nurseFieldName(params.getRow());

            boolean timeSet = fields.setField(timeField, safe(params.getTimeValue()));
            boolean contentSet = fields.setField(contentField, safe(params.getTextValue()));
            boolean nurseSet = fields.setField(nurseField, safe(params.getNurse()));
            if (!timeSet || !contentSet || !nurseSet) {
                throw new IllegalStateException("Failed to populate form fields for row " + params.getRow());
            }

            String signatureField = NursingRecordTemplate.signatureFieldName(params.getRow());
            if (fields.getFieldItem(signatureField) == null) {
                throw new IllegalStateException("Signature field missing: " + signatureField);
            }

            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setReason(params.getReason());
            appearance.setLocation(params.getLocation());
            appearance.setContact(params.getContact());
            appearance.setSignDate(Calendar.getInstance());
            appearance.setVisibleSignature(signatureField);
            String layer2Text = String.format("%s\n%s\n%s",
                    safe(params.getNurse()),
                    safe(params.getTimeValue()),
                    safe(params.getTextValue()));
            appearance.setLayer2Text(layer2Text);

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

    private static String safe(String value) {
        return value == null ? "" : value.stripTrailing();
    }
}
