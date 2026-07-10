package com.cloudops.common.bootstrap;

import com.cloudops.common.config.SecretStoreProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PlatformSecretStore {

    private static final Logger log = LoggerFactory.getLogger(PlatformSecretStore.class);
    private static final String KEY_JWT = "jwt.secret";
    private static final String KEY_CREDENTIALS = "credentials.master-key";

    private final PlatformSecrets secrets;

    public PlatformSecretStore(SecretStoreProperties properties, Environment environment) {
        this.secrets = loadOrCreate(properties.path(), environment);
    }

    public PlatformSecrets secrets() {
        return secrets;
    }

    private PlatformSecrets loadOrCreate(String pathValue, Environment environment) {
        String envJwt = blankToNull(environment.getProperty("JWT_SECRET"));
        String envCredentials = blankToNull(environment.getProperty("CREDENTIALS_MASTER_KEY"));
        if (envJwt != null && envCredentials != null) {
            log.info("Using JWT_SECRET and CREDENTIALS_MASTER_KEY from environment variables");
            return new PlatformSecrets(envJwt, envCredentials);
        }

        Path path = Path.of(pathValue);
        Properties fileProps = readFile(path);
        String fileJwt = fileProps.getProperty(KEY_JWT);
        String fileCredentials = fileProps.getProperty(KEY_CREDENTIALS);

        String jwt = firstNonBlank(envJwt, fileJwt, generateSecret());
        String credentials = firstNonBlank(envCredentials, fileCredentials, generateSecret());

        boolean generatedJwt = envJwt == null && (fileJwt == null || fileJwt.isBlank());
        boolean generatedCredentials = envCredentials == null && (fileCredentials == null || fileCredentials.isBlank());
        if (generatedJwt || generatedCredentials) {
            persist(path, jwt, credentials);
            log.info("Generated and persisted platform secrets to {}", path.toAbsolutePath());
        }

        return new PlatformSecrets(jwt, credentials);
    }

    private void persist(Path path, String jwt, String credentials) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Properties props = new Properties();
            props.setProperty(KEY_JWT, jwt);
            props.setProperty(KEY_CREDENTIALS, credentials);
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(temp)) {
                props.store(out, "CloudOps platform secrets - DO NOT COMMIT");
            }
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            try {
                path.toFile().setReadable(false, false);
                path.toFile().setWritable(false, false);
                path.toFile().setReadable(true, true);
                path.toFile().setWritable(true, true);
            } catch (Exception ignored) {
                // best-effort on Windows
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist platform secrets to " + path, ex);
        }
    }

    private Properties readFile(Path path) {
        Properties props = new Properties();
        if (!Files.exists(path)) {
            return props;
        }
        try (var in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read secrets file: " + path, ex);
        }
        return props;
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "base64:" + Base64.getEncoder().encodeToString(bytes);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return generateSecret();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
