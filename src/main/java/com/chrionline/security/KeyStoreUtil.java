package com.chrionline.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Optional;

public class KeyStoreUtil {

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String DEFAULT_KEYSTORE_PATH = "config/keystore.p12";
    private static final String DEFAULT_KEY_ALIAS = "chrionline-server";
    private static final String DEFAULT_PASSWORD = 
        Optional.ofNullable(System.getenv("KEYSTORE_PASSWORD"))
                .filter(s -> !s.isBlank())
                .orElse("changeit");

    static {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    private KeyStoreUtil() {}

    public static KeyStore loadKeyStore(String keystorePath, String password) throws Exception {
        Path path = Paths.get(keystorePath);
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        if (Files.exists(path)) {
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keyStore.load(fis, password.toCharArray());
            }
        } else {
            keyStore.load(null, password.toCharArray());
        }
        return keyStore;
    }

    public static void saveKeyStore(KeyStore keyStore, String keystorePath, String password) throws Exception {
        Path path = Paths.get(keystorePath);
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
            keyStore.store(fos, password.toCharArray());
        }
    }

    public static KeyPair getOrGenerateKeyPair(String keystorePath, String alias, String storePassword, String keyPassword) throws Exception {
        KeyStore keyStore = loadKeyStore(keystorePath, storePassword);
        
        if (keyStore.isKeyEntry(alias)) {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword.toCharArray());
            Certificate cert = keyStore.getCertificate(alias);
            PublicKey publicKey = cert.getPublicKey();
            return new KeyPair(publicKey, privateKey);
        } else {
            KeyPair keyPair = RSAUtil.generateKeyPair();
            X509Certificate cert = generateSelfSignedCertificate(keyPair);
            keyStore.setKeyEntry(alias, keyPair.getPrivate(), keyPassword.toCharArray(), new Certificate[]{cert});
            saveKeyStore(keyStore, keystorePath, storePassword);
            return keyPair;
        }
    }

    public static KeyPair getOrGenerateKeyPair() throws Exception {
        return getOrGenerateKeyPair(DEFAULT_KEYSTORE_PATH, DEFAULT_KEY_ALIAS, DEFAULT_PASSWORD, DEFAULT_PASSWORD);
    }

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        X500Name issuer = new X500Name("CN=ChriOnline Server");
        BigInteger serial = new BigInteger(64, new SecureRandom());
        Date notBefore = new Date(System.currentTimeMillis() - 86400000L);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24L * 60L * 60L * 1000L);
        X500Name subject = issuer;
        PublicKey publicKey = keyPair.getPublic();

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, publicKey);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }
}
