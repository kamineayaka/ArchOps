package com.archops.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretBoxTest {

    @Test
    void roundTripEncryptDecrypt() {
        SecretBox box = new SecretBox("");
        String cipher = box.encrypt("hunter2");
        assertThat(cipher).isNotEqualTo("hunter2");
        assertThat(box.decrypt(cipher)).isEqualTo("hunter2");
    }
}
