package com.archops.executor.tls;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Self-signed mTLS fixtures for Compose/CI (ADR-0045). Not a human-operated PKI.
 */
public final class MtlsPemFiles {

    private final Path directory;
    private final Path caCert;
    private final Path serverCert;
    private final Path serverKey;
    private final Path clientCert;
    private final Path clientKey;
    private final Path wrongClientCert;
    private final Path wrongClientKey;

    public MtlsPemFiles(
            Path directory,
            Path caCert,
            Path serverCert,
            Path serverKey,
            Path clientCert,
            Path clientKey,
            Path wrongClientCert,
            Path wrongClientKey
    ) {
        this.directory = directory;
        this.caCert = caCert;
        this.serverCert = serverCert;
        this.serverKey = serverKey;
        this.clientCert = clientCert;
        this.clientKey = clientKey;
        this.wrongClientCert = wrongClientCert;
        this.wrongClientKey = wrongClientKey;
    }

    public static MtlsPemFiles generate() {
        try {
            return generateTo(Files.createTempDirectory("archops-executor-mtls"));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static MtlsPemFiles generateTo(Path directory) {
        try {
            Files.createDirectories(directory);
            Path caKey = directory.resolve("ca.key");
            Path caCert = directory.resolve("ca.crt");
            Path serverKey = directory.resolve("server.key");
            Path serverCsr = directory.resolve("server.csr");
            Path serverCert = directory.resolve("server.crt");
            Path serverExt = directory.resolve("server-san.ext");
            Path clientKey = directory.resolve("client.key");
            Path clientCsr = directory.resolve("client.csr");
            Path clientCert = directory.resolve("client.crt");
            Path wrongKey = directory.resolve("wrong.key");
            Path wrongCert = directory.resolve("wrong.crt");

            openssl("req", "-x509", "-newkey", "rsa:2048", "-sha256", "-days", "3650", "-nodes",
                    "-keyout", caKey.toString(), "-out", caCert.toString(),
                    "-subj", "/CN=archops-dev-ca");
            openssl("req", "-newkey", "rsa:2048", "-nodes",
                    "-keyout", serverKey.toString(), "-out", serverCsr.toString(),
                    "-subj", "/CN=executor");
            Files.writeString(serverExt, "subjectAltName=DNS:localhost,DNS:executor,IP:127.0.0.1\n");
            openssl("x509", "-req", "-in", serverCsr.toString(), "-CA", caCert.toString(),
                    "-CAkey", caKey.toString(), "-CAcreateserial", "-out", serverCert.toString(),
                    "-days", "3650", "-sha256", "-extfile", serverExt.toString());
            openssl("req", "-newkey", "rsa:2048", "-nodes",
                    "-keyout", clientKey.toString(), "-out", clientCsr.toString(),
                    "-subj", "/CN=control-plane");
            openssl("x509", "-req", "-in", clientCsr.toString(), "-CA", caCert.toString(),
                    "-CAkey", caKey.toString(), "-CAcreateserial", "-out", clientCert.toString(),
                    "-days", "3650", "-sha256");
            openssl("req", "-x509", "-newkey", "rsa:2048", "-sha256", "-days", "3650", "-nodes",
                    "-keyout", wrongKey.toString(), "-out", wrongCert.toString(),
                    "-subj", "/CN=not-control-plane");
            Files.deleteIfExists(serverCsr);
            Files.deleteIfExists(serverExt);
            Files.deleteIfExists(clientCsr);
            Files.deleteIfExists(directory.resolve("ca.srl"));
            return new MtlsPemFiles(directory, caCert, serverCert, serverKey, clientCert, clientKey, wrongCert, wrongKey);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate executor mTLS fixtures", ex);
        }
    }

    public Path directory() {
        return directory;
    }

    public Path caCert() {
        return caCert;
    }

    public Path serverCert() {
        return serverCert;
    }

    public Path serverKey() {
        return serverKey;
    }

    public Path clientCert() {
        return clientCert;
    }

    public Path clientKey() {
        return clientKey;
    }

    public Path wrongClientCert() {
        return wrongClientCert;
    }

    public Path wrongClientKey() {
        return wrongClientKey;
    }

    private static void openssl(String... args) throws IOException {
        List<String> command = new java.util.ArrayList<>();
        command.add("openssl");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        try {
            int code = process.waitFor();
            if (code != 0) {
                throw new IOException("openssl failed (" + code + "): " + output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("openssl interrupted", ex);
        }
    }
}
