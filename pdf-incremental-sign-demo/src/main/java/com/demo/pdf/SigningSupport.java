package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

final class SigningSupport {

    private SigningSupport() {
    }

    static SigningContext resolve(String pkcs12Path, String password) throws Exception {
        if (pkcs12Path != null && !pkcs12Path.isBlank()) {
            char[] pwd = toPassword(password);
            KeyStore ks = DemoKeystoreUtil.loadKeyStore(pkcs12Path, pwd);
            KeyStore.PrivateKeyEntry entry = DemoKeystoreUtil.firstPrivateKey(ks, pwd);
            return new SigningContext(entry.getPrivateKey(), entry.getCertificateChain());
        }
        Path tmp = DemoKeystoreUtil.createDemoP12();
        char[] demoPassword = "123456".toCharArray();
        KeyStore ks = DemoKeystoreUtil.loadKeyStore(tmp.toString(), demoPassword);
        KeyStore.PrivateKeyEntry entry = DemoKeystoreUtil.firstPrivateKey(ks, demoPassword);
        return new SigningContext(entry.getPrivateKey(), entry.getCertificateChain());
    }

    private static char[] toPassword(String password) {
        return password != null ? password.toCharArray() : new char[0];
    }

    record SigningContext(PrivateKey privateKey, Certificate[] chain) {
    }
}
