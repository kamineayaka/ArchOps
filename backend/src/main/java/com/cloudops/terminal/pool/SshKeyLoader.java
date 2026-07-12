package com.cloudops.terminal.pool;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Iterator;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;

/** Parses OpenSSH / PEM private keys for pooled SSH sessions. */
public final class SshKeyLoader {

    private SshKeyLoader() {}

    public static KeyPair loadPrivateKey(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Private key is empty");
        }
        try (InputStream in = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
            Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                    null,
                    NamedResource.ofName("cloudops-key"),
                    in,
                    FilePasswordProvider.EMPTY);
            if (keyPairs == null) {
                throw new IllegalArgumentException("No key pairs found in private key material");
            }
            Iterator<KeyPair> iterator = keyPairs.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalArgumentException("No key pairs found in private key material");
            }
            return iterator.next();
        }
    }
}
