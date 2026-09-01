package com.archops.executor.tls;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;

import java.io.File;
import java.nio.file.Path;

/**
 * Shared gRPC mTLS contexts for the 执行引擎 server and 控制面 client.
 */
public final class ExecutorMtls {

    private ExecutorMtls() {
    }

    public static SslContext serverContext(Path serverCert, Path serverKey, Path caCert) {
        try {
            return GrpcSslContexts.forServer(file(serverCert), file(serverKey))
                    .trustManager(file(caCert))
                    .clientAuth(ClientAuth.REQUIRE)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build 执行引擎 mTLS server context", ex);
        }
    }

    public static SslContext clientContext(Path clientCert, Path clientKey, Path caCert) {
        try {
            return GrpcSslContexts.forClient()
                    .keyManager(file(clientCert), file(clientKey))
                    .trustManager(file(caCert))
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build 控制面 mTLS client context", ex);
        }
    }

    private static File file(Path path) {
        if (path == null || path.toString().isBlank()) {
            throw new IllegalStateException("mTLS PEM path is required");
        }
        File file = path.toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("mTLS PEM is missing: " + path);
        }
        return file;
    }
}
