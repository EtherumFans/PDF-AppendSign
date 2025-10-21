package com.demo.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

public final class DemoKeystoreUtil {

    private DemoKeystoreUtil() {
    }

    public static void ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static Path createDemoP12() throws Exception {
        Path temp = Files.createTempFile("demo-nurse", ".p12");
        createDemoP12(temp, "123456".toCharArray(), "Demo Nurse");
        return temp;
    }

    public static void createDemoP12(Path target, char[] password, String commonName) throws Exception {
        ensureProvider();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X500Name subject = new X500Name("CN=" + commonName);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 3600_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 3600_000L);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                kp.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(kp.getPrivate());
        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
        cert.checkValidity(new Date());
        cert.verify(kp.getPublic());

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        Certificate[] chain = new Certificate[]{cert};
        ks.setKeyEntry("demo", kp.getPrivate(), password, chain);

        try (OutputStream os = Files.newOutputStream(target)) {
            ks.store(os, password);
        }
    }

    public static KeyStore loadKeyStore(String pkcs12Path, char[] password) throws Exception {
        ensureProvider();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(Path.of(pkcs12Path))) {
            ks.load(is, password);
        }
        return ks;
    }

    public static KeyStore.PrivateKeyEntry firstPrivateKey(KeyStore ks, char[] password) throws Exception {
        for (String alias : java.util.Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                Key key = ks.getKey(alias, password);
                if (key instanceof PrivateKey) {
                    Certificate[] chain = ks.getCertificateChain(alias);
                    return new KeyStore.PrivateKeyEntry((PrivateKey) key, chain);
                }
            }
        }
        throw new IllegalStateException("No private key entry found in keystore");
    }
}
