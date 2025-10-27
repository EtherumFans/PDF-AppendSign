package com.demo.pdf;

import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.DigestAlgorithms;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PrivateKeySignature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileOutputStream;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Minimal DocMDP helper implemented with iText 5 APIs.
 */
public final class DocMDPUtil {

    private static final String CERTIFICATION_CONTACT = "docmdp-cert@example.com";
    private static final TimeZone SIGN_TIMEZONE = TimeZone.getTimeZone("UTC");

    private DocMDPUtil() {
    }

    public static boolean hasCertification(String path) throws Exception {
        try (PdfReader reader = new PdfReader(path)) {
            PdfDictionary perms = reader.getCatalog().getAsDict(PdfName.PERMS);
            return perms != null && perms.contains(PdfName.DOCMDP);
        }
    }

    public static void certify(String src,
                                String dest,
                                PrivateKey key,
                                X509Certificate[] chain,
                                int certificationLevel) throws Exception {
        if (chain == null || chain.length == 0) {
            throw new IllegalArgumentException("Certification requires a non-empty certificate chain");
        }
        ensureProvider();
        try (PdfReader reader = new PdfReader(src);
             FileOutputStream os = new FileOutputStream(dest)) {
            PdfStamper stamper = PdfStamper.createSignature(reader, os, '\0', null, true);
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            appearance.setCertificationLevel(certificationLevel);
            appearance.setVisibleSignature(new Rectangle(0, 0, 0, 0), 1, "docmdp");
            appearance.setReason("DocMDP Certification");
            appearance.setLocation("Certification");
            appearance.setContact(CERTIFICATION_CONTACT);
            appearance.setSignDate(Calendar.getInstance(SIGN_TIMEZONE));
            appearance.setCertificate(chain[0]);

            ExternalDigest digest = new BouncyCastleDigest();
            ExternalSignature signature = new PrivateKeySignature(key, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            MakeSignature.signDetached(appearance, digest, signature, chain, null, null, null, 0, MakeSignature.CryptoStandard.CMS);
        }
    }

    public static void ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
