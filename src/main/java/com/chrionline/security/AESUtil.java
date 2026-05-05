package com.chrionline.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Module AES (Achraf) - chiffrement symetrique pour la phase hybride RSA -> AES.
 *
 * <p>Choix techniques :
 * <ul>
 *   <li>AES-256 (cle 32 octets) en mode <b>GCM</b> (authentifie : confidentialite + integrite).</li>
 *   <li>IV de 12 octets (recommande pour GCM), genere via {@link SecureRandom}.</li>
 *   <li>Tag d'authentification de 128 bits.</li>
 * </ul>
 *
 * <p>Utilisation typique cote client :
 * <pre>
 *   SecretKey aes = AESUtil.generateKey();
 *   AESUtil.Sealed sealed = AESUtil.encrypt(aes, "{ \"order\": 42 }");
 *   String b64Cipher = sealed.cipherTextBase64();
 *   String b64Iv     = sealed.ivBase64();
 * </pre>
 */
public final class AESUtil {

    public static final String ALGORITHM       = "AES";
    public static final String TRANSFORMATION  = "AES/GCM/NoPadding";
    public static final int    KEY_SIZE_BITS   = 256;
    public static final int    IV_LENGTH_BYTES = 12;
    public static final int    TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AESUtil() {}

    /** Genere une cle AES-256 fraiche. */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
        kg.init(KEY_SIZE_BITS, RANDOM);
        return kg.generateKey();
    }

    /** Genere un IV (nonce) de 12 octets pour AES-GCM. */
    public static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        RANDOM.nextBytes(iv);
        return iv;
    }

    /** Encode la cle en Base64 (utile pour la chiffrer ensuite avec RSA). */
    public static String encodeKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Recree une SecretKey a partir d'octets bruts (issus du dechiffrement RSA). */
    public static SecretKey keyFromBytes(byte[] raw) {
        return new SecretKeySpec(raw, ALGORITHM);
    }

    /** Recree une SecretKey a partir d'une chaine Base64. */
    public static SecretKey keyFromBase64(String base64) {
        return keyFromBytes(Base64.getDecoder().decode(base64));
    }

    /**
     * Chiffre {@code plaintext} avec la cle AES en GCM.
     * Retourne le couple (cipherText Base64, IV Base64).
     */
    public static Sealed encrypt(SecretKey key, String plaintext) throws Exception {
        byte[] iv = generateIv();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return new Sealed(
                Base64.getEncoder().encodeToString(cipherText),
                Base64.getEncoder().encodeToString(iv)
        );
    }

    /** Dechiffre un cipherText/IV (tous deux Base64) avec la cle AES. */
    public static String decrypt(SecretKey key, String cipherTextBase64, String ivBase64) throws Exception {
        byte[] iv         = Base64.getDecoder().decode(ivBase64);
        byte[] cipherText = Base64.getDecoder().decode(cipherTextBase64);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] plain = cipher.doFinal(cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** Couple (cipher, iv) en Base64 retourne par {@link #encrypt(SecretKey, String)}. */
    public record Sealed(String cipherTextBase64, String ivBase64) {}
}
