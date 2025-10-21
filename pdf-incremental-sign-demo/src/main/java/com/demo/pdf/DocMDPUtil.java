package com.demo.pdf;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public final class DocMDPUtil {

    private DocMDPUtil() {
    }

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
        signer.setCertificationLevel(permission.getCertificationLevel());
        signer.setFieldName("docmdp");
        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        appearance.setReuseAppearance(false);
        appearance.setPageRect(new Rectangle(0, 0, 0, 0));
        appearance.setPageNumber(1);
        appearance.setReason("DocMDP Certification");
        appearance.setLocation("Certification");
        IExternalSignature signature = new PrivateKeySignature(key, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
        IExternalDigest digest = new BouncyCastleDigest();
        signer.signDetached(digest, signature, chain, null, null, null, 0, PdfSigner.CryptoStandard.CADES);
    }
}
