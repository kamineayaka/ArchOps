package com.archops.common.crypto;

import com.archops.support.TestCredentialEncryptionKey;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretBoxTest {

    @Test
    void roundTripEncryptDecrypt() {
        SecretBox box = new SecretBox(TestCredentialEncryptionKey.BASE64);
        String cipher = box.encrypt("hunter2");
        assertThat(cipher).isNotEqualTo("hunter2");
        assertThat(box.decrypt(cipher)).isEqualTo("hunter2");
    }

    @Test
    void blankKeyMustNotEncryptWithPublicConstant() {
        String publicDefault = "0123456789abcdef0123456789abcdef";
        String publicDefaultBase64 = Base64.getEncoder()
                .encodeToString(publicDefault.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new SecretBox(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64")
                .hasMessageNotContaining(publicDefault)
                .hasMessageNotContaining(publicDefaultBase64);
        assertThatThrownBy(() -> new SecretBox("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64");
        assertThatThrownBy(() -> new SecretBox(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64");

        SecretBox explicitPublicDefault = new SecretBox(publicDefaultBase64);
        String probe = "probe-secret";
        String cipherFromExplicit = explicitPublicDefault.encrypt(probe);
        assertThat(cipherFromExplicit).isNotEqualTo(probe);
        assertThat(explicitPublicDefault.decrypt(cipherFromExplicit)).isEqualTo(probe);
    }
}
