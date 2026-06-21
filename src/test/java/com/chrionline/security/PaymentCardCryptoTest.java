package com.chrionline.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PaymentCardCryptoTest {
    @Test
    void cardNumber_isEncryptedAndRecoverable() {
        String cardNumber = "4111111111111111";
        AESUtil.Sealed sealed = PaymentCardCrypto.encryptCardNumber(cardNumber);

        assertNotEquals(cardNumber, sealed.cipherTextBase64());
        assertEquals(cardNumber, PaymentCardCrypto.decryptCardNumber(sealed.cipherTextBase64(), sealed.ivBase64()));
        assertEquals("1111", PaymentCardCrypto.last4(cardNumber));
        assertEquals("VISA", PaymentCardCrypto.detectBrand(cardNumber));
    }
}
