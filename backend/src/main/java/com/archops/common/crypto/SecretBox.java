package com.archops.common.crypto;

import com.archops.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for host SSH secrets. Ciphertext only is persisted; never log plaintext.
 */
@Component
public class SecretBox {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(
            @Value("${archops.credentials.encryption-key-base64:}") String keyBase64
    ) {
        byte[] raw;
        if (keyBase64 == null || keyBase64.isBlank()) {
            // Dev/test default (32 bytes) — production MUST set ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64.
            raw = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        } else {
            raw = Base64.getDecoder().decode(keyBase64.trim());
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException("archops.credentials.encryption-key-base64 must decode to 16/24/32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new BusinessException("CREDENTIAL_SECRET_REQUIRED", "SSH secret is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buf.put(iv);
            buf.put(cipherBytes);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("CREDENTIAL_ENCRYPT_FAILED", "Failed to encrypt SSH secret");
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new BusinessException("CREDENTIAL_MISSING", "Encrypted SSH secret missing");
        }
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext);
            if (all.length <= IV_BYTES) {
                throw new BusinessException("CREDENTIAL_DECRYPT_FAILED", "Invalid ciphertext");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] body = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, body, 0, body.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new BusinessException("CREDENTIAL_DECRYPT_FAILED", "Failed to decrypt SSH secret");
        }
    }
}
