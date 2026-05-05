package com.chrionline.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Helpers RSA -> AES (chiffrement hybride).
 *
 * <p>RSA chiffre uniquement la cle AES (32 octets) ; AES chiffre les donnees utiles.
 * Cela offre la securite asymetrique de RSA pour l'echange de cle, et la performance
 * d'AES-GCM pour le payload reel.
 *
 * <p>Mode RSA : <b>RSA/ECB/OAEPWithSHA-256AndMGF1Padding</b> (recommande, pas de PKCS1v1.5).
 */
public final class HybridCryptoUtil {

    public static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private HybridCryptoUtil() {}

    /** Chiffre la cle AES avec la cle publique RSA du serveur. Retourne du Base64. */
    public static String wrapAesKey(SecretKey aesKey, PublicKey rsaPublicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        byte[] wrapped = cipher.doFinal(aesKey.getEncoded());
        return Base64.getEncoder().encodeToString(wrapped);
    }

    /** Dechiffre la cle AES (Base64) avec la cle privee RSA du serveur. */
    public static SecretKey unwrapAesKey(String wrappedBase64, PrivateKey rsaPrivateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] raw = cipher.doFinal(Base64.getDecoder().decode(wrappedBase64));
        return AESUtil.keyFromBytes(raw);
    }
}
