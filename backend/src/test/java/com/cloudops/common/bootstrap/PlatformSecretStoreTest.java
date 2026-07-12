package com.cloudops.common.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudops.common.config.SecretStoreProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class PlatformSecretStoreTest {

    @Test
    void usesEnvWhenBothSet(@TempDir Path tempDir) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("JWT_SECRET", "env-jwt")
                .withProperty("CREDENTIALS_MASTER_KEY", "env-cred");
        Path secretsPath = tempDir.resolve("secrets.properties");

        PlatformSecretStore store =
                new PlatformSecretStore(new SecretStoreProperties(secretsPath.toString()), env);

        assertThat(store.secrets().jwtSecret()).isEqualTo("env-jwt");
        assertThat(store.secrets().credentialsMasterKey()).isEqualTo("env-cred");
        assertThat(Files.exists(secretsPath)).isFalse();
    }

    @Test
    void usesFileWhenEnvNotSet(@TempDir Path tempDir) throws IOException {
        Path secretsPath = tempDir.resolve("secrets.properties");
        Properties props = new Properties();
        props.setProperty("jwt.secret", "file-jwt");
        props.setProperty("credentials.master-key", "file-cred");
        try (var out = Files.newOutputStream(secretsPath)) {
            props.store(out, "test");
        }

        MockEnvironment env = new MockEnvironment();
        PlatformSecretStore store =
                new PlatformSecretStore(new SecretStoreProperties(secretsPath.toString()), env);

        assertThat(store.secrets().jwtSecret()).isEqualTo("file-jwt");
        assertThat(store.secrets().credentialsMasterKey()).isEqualTo("file-cred");
    }

    @Test
    void envOverridesFileForIndividualKeys(@TempDir Path tempDir) throws IOException {
        Path secretsPath = tempDir.resolve("secrets.properties");
        Properties props = new Properties();
        props.setProperty("jwt.secret", "file-jwt");
        props.setProperty("credentials.master-key", "file-cred");
        try (var out = Files.newOutputStream(secretsPath)) {
            props.store(out, "test");
        }

        MockEnvironment env = new MockEnvironment().withProperty("JWT_SECRET", "env-jwt");
        PlatformSecretStore store =
                new PlatformSecretStore(new SecretStoreProperties(secretsPath.toString()), env);

        assertThat(store.secrets().jwtSecret()).isEqualTo("env-jwt");
        assertThat(store.secrets().credentialsMasterKey()).isEqualTo("file-cred");
    }

    @Test
    void generatesAndPersistsWhenMissing(@TempDir Path tempDir) throws IOException {
        Path secretsPath = tempDir.resolve("secrets.properties");
        MockEnvironment env = new MockEnvironment();

        PlatformSecretStore store =
                new PlatformSecretStore(new SecretStoreProperties(secretsPath.toString()), env);

        assertThat(store.secrets().jwtSecret()).startsWith("base64:");
        assertThat(store.secrets().credentialsMasterKey()).startsWith("base64:");
        assertThat(Files.exists(secretsPath)).isTrue();

        Properties loaded = new Properties();
        try (var in = Files.newInputStream(secretsPath)) {
            loaded.load(in);
        }
        assertThat(loaded.getProperty("jwt.secret")).isEqualTo(store.secrets().jwtSecret());
        assertThat(loaded.getProperty("credentials.master-key"))
                .isEqualTo(store.secrets().credentialsMasterKey());
    }

    @Test
    void reusesPersistedSecretsOnRestart(@TempDir Path tempDir) {
        Path secretsPath = tempDir.resolve("secrets.properties");
        MockEnvironment env = new MockEnvironment();

        PlatformSecretStore first =
                new PlatformSecretStore(new SecretStoreProperties(secretsPath.toString()), env);
        String jwt = first.secrets().jwtSecret();
        String cred = first.secrets().credentialsMasterKey();

        PlatformSecretStore second =
                new PlatformSecretStore(new SecretStoreProperties(secretsPath.toString()), env);

        assertThat(second.secrets().jwtSecret()).isEqualTo(jwt);
        assertThat(second.secrets().credentialsMasterKey()).isEqualTo(cred);
    }
}
