package com.archops.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeCredentialEncryptionKeyTest {

    private static final Pattern BLANK_DEFAULT = Pattern.compile(
            "ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64:\\s*\\$\\{ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64:-");

    @Test
    void hubAndExecutorRequireTheSameKeyWithoutBlankDefault() throws Exception {
        Path root = repoRoot();
        String compose = Files.readString(root.resolve("deploy/compose/compose.yaml"));
        String envExample = Files.readString(root.resolve("deploy/compose/.env.example"));

        assertThat(BLANK_DEFAULT.matcher(compose).find())
                .as("Compose must not default the encryption key to blank")
                .isFalse();
        assertThat(compose).doesNotContain("0123456789abcdef");

        int assignments = Pattern.compile("ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64:")
                .matcher(compose)
                .results()
                .toList()
                .size();
        assertThat(assignments)
                .as("control plane and 执行引擎 must both receive the key")
                .isEqualTo(2);
        assertThat(compose).contains(
                "ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64: ${ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64}");

        assertThat(envExample).contains("ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64=");
        assertThat(envExample).contains("openssl rand -base64 32");
        assertThat(envExample.toLowerCase()).contains("same");
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            if (Files.isRegularFile(dir.resolve("deploy/compose/compose.yaml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not find deploy/compose/compose.yaml from " + Path.of("").toAbsolutePath());
    }
}
