package com.cloudops.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.cloudops.common.bootstrap.PlatformSecrets;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM credential encryption. The master key is derived from the
 * configured {@code cloudops.credentials.master-key} and never persisted.
 * Ciphertext + IV are stored separately so rotations remain feasible.
 */
@Component
public class CredentialCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int KEY_BYTES = 32;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(PlatformSecrets platformSecrets) {
        this.secretKey = deriveKey(platformSecrets.credentialsMasterKey());
    }

    public EncryptedSecret encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(cipherBytes, iv);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt credential", ex);
        }
    }

    public String decrypt(byte[] cipherBytes, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt credential", ex);
        }
    }

    private SecretKey deriveKey(String masterKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(masterKey.getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = Arrays.copyOf(hash, KEY_BYTES);
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to derive credential master key", ex);
        }
    }

    public record EncryptedSecret(byte[] cipher, byte[] iv) {}
}
