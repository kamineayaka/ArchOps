package com.archops.support;

/**
 * Explicit AES-256 fixture for tests. Not a production or Compose default.
 * Hub HTTP tests and the in-process 执行引擎 fixture must share this value.
 */
public final class TestCredentialEncryptionKey {

    public static final String BASE64 = "YXJjaG9wcy1maXh0dXJlLWFlcy0yNTYta2V5ISEhISE=";

    public static final String SPRING_PROPERTY =
            "archops.credentials.encryption-key-base64=" + BASE64;

    private TestCredentialEncryptionKey() {
    }
}
