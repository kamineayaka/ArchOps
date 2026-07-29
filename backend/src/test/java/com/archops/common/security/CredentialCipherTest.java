package com.archops.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.archops.common.bootstrap.PlatformSecrets;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CredentialCipherTest {

    private CredentialCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new CredentialCipher(new PlatformSecrets(
                "unit-test-jwt-secret-must-be-at-least-32-bytes!!",
                "unit-test-credentials-master-key"));
    }

    @Test
    void roundTripsPlaintext() {
        CredentialCipher.EncryptedSecret encrypted = cipher.encrypt("s3cret-password");
        assertEquals("s3cret-password", cipher.decrypt(encrypted.cipher(), encrypted.iv()));
        assertEquals(12, encrypted.iv().length);
        assertFalse(Arrays.equals("s3cret-password".getBytes(), encrypted.cipher()));
    }

    @Test
    void usesFreshIvEachEncrypt() {
        CredentialCipher.EncryptedSecret a = cipher.encrypt("same");
        CredentialCipher.EncryptedSecret b = cipher.encrypt("same");
        assertFalse(Arrays.equals(a.iv(), b.iv()));
        assertNotEquals(Arrays.toString(a.cipher()), Arrays.toString(b.cipher()));
    }

    @Test
    void rejectsTamperedCiphertext() {
        CredentialCipher.EncryptedSecret encrypted = cipher.encrypt("plain");
        encrypted.cipher()[0] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted.cipher(), encrypted.iv()));
    }
}
