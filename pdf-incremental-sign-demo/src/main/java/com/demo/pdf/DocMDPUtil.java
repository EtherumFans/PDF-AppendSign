package com.demo.pdf;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDate;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import com.itextpdf.signatures.PdfSignature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.TimeZone;

public final class DocMDPUtil {

    private DocMDPUtil() {
    }

    private static final String CERTIFICATION_CONTACT = "docmdp-cert@example.com";
    private static final TimeZone SIGN_TIMEZONE = TimeZone.getTimeZone("UTC");

    public enum Permission {
        FORM_FILL_AND_SIGNATURES(PdfSigner.CERTIFIED_FORM_FILLING_AND_ANNOTATIONS);

        private final int certificationLevel;

        Permission(int certificationLevel) {
            this.certificationLevel = certificationLevel;
        }

        public int getCertificationLevel() {
            return certificationLevel;
        }
    }

    public static boolean hasDocMDP(PdfDocument document) {
        PdfDictionary perms = document.getCatalog().getPdfObject().getAsDictionary(PdfName.Perms);
        return perms != null && perms.containsKey(PdfName.DocMDP);
    }

    public static void applyCertification(PdfSigner signer, PrivateKey key, X509Certificate[] chain, Permission permission)
            throws GeneralSecurityException, IOException {
        if (chain == null || chain.length == 0) {
            throw new IllegalArgumentException("Certification requires a non-empty certificate chain");
        }
        String location = "Certification";
        String reason = "DocMDP Certification";
        signer.setCertificationLevel(permission.getCertificationLevel());
        signer.setFieldName("docmdp");
        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        appearance.setReuseAppearance(false);
        appearance.setPageRect(new Rectangle(0, 0, 0, 0));
        appearance.setPageNumber(1);
        appearance.setReason(reason);
        appearance.setLocation(location);
        appearance.setContact(CERTIFICATION_CONTACT);
        Calendar signDate = Calendar.getInstance(SIGN_TIMEZONE);
        appearance.setCertificate(chain[0]);
        signer.setSignDate(signDate);
        signer.setSignatureEvent(signature -> configureCertificationDictionary(signature, chain[0], reason, location, signDate));
        IExternalSignature signature = new PrivateKeySignature(key, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
        IExternalDigest digest = new BouncyCastleDigest();
        signer.signDetached(digest, signature, chain, null, null, null, 0, PdfSigner.CryptoStandard.CADES);
    }

    private static void configureCertificationDictionary(PdfSignature signature, X509Certificate signerCert,
                                                          String reason, String location, Calendar signDate) {
        if (signature == null) {
            return;
        }
        signature.put(PdfName.Type, PdfName.Sig);
        signature.put(PdfName.Filter, PdfName.Adobe_PPKLite);
        signature.put(PdfName.SubFilter, new PdfName("ETSI.CAdES.detached"));
        if (signDate != null) {
            signature.put(PdfName.M, new PdfDate(signDate).getPdfObject());
        }
        if (signerCert != null) {
            signature.put(PdfName.Name, new PdfString(signerCert.getSubjectX500Principal().getName()));
        }
        if (reason != null && !reason.isBlank()) {
            signature.put(PdfName.Reason, new PdfString(reason));
        }
        if (location != null && !location.isBlank()) {
            signature.put(PdfName.Location, new PdfString(location));
        }
        signature.put(PdfName.ContactInfo, new PdfString(CERTIFICATION_CONTACT));
    }
}
