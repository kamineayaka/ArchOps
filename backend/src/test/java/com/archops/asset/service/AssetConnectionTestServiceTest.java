package com.archops.asset.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.domain.SshAuthType;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.asset.type.AssetTypeRegistry;
import com.archops.asset.type.DatabaseAssetTypeHandler;
import com.archops.asset.type.K8sAssetTypeHandler;
import com.archops.asset.type.ServerAssetTypeHandler;
import com.archops.common.security.CredentialCipher;
import com.archops.terminal.pool.AssetSshDialer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetConnectionTestServiceTest {

    private AssetSshDialer dialer;
    private AssetRepository assetRepository;
    private AssetConnectionTestService service;

    @BeforeEach
    void setUp() {
        dialer = mock(AssetSshDialer.class);
        assetRepository = mock(AssetRepository.class);
        SshCredentialRepository credentialRepository = mock(SshCredentialRepository.class);
        CredentialCipher cipher = mock(CredentialCipher.class);
        AssetTypeRegistry registry = new AssetTypeRegistry(List.of(
                new ServerAssetTypeHandler(dialer),
                new DatabaseAssetTypeHandler(),
                new K8sAssetTypeHandler(dialer)));
        service = new AssetConnectionTestService(
                registry, assetRepository, credentialRepository, cipher, new ObjectMapper());
    }

    @Test
    void testsSavedServerViaSharedDialer() throws Exception {
        Asset asset = new Asset();
        asset.setId(9L);
        asset.setKind(AssetKind.SERVER);
        when(assetRepository.findById(9L)).thenReturn(Optional.of(asset));

        ClientSession session = mock(ClientSession.class);
        when(session.isAuthenticated()).thenReturn(true);
        when(dialer.dial(9L)).thenReturn(session);

        TestConnectionResponse res = service.test(new TestConnectionRequest(
                9L, null, null, null, null, null, null, null, null, null, null));

        assertTrue(res.ok());
        verify(dialer).dial(9L);
        verify(session).close(false);
    }

    @Test
    void testsEphemeralServerCredentialsViaDialer() throws Exception {
        ClientSession session = mock(ClientSession.class);
        when(session.isAuthenticated()).thenReturn(true);
        when(dialer.dialEphemeral(anyList(), anyString(), anyInt(), anyString(), any(), anyString(), anyLong()))
                .thenReturn(session);

        TestConnectionResponse res = service.test(new TestConnectionRequest(
                null, AssetKind.SERVER, "10.0.0.1", 22, "root", SshAuthType.PASSWORD, "secret", List.of(), null, null, null));

        assertTrue(res.ok());
        verify(dialer).dialEphemeral(
                eq(List.of()), eq("10.0.0.1"), eq(22), eq("root"),
                eq(SshAuthType.PASSWORD), eq("secret"), eq(10_000L));
    }

    @Test
    void returnsFailureWhenHostMissing() {
        TestConnectionResponse res = service.test(new TestConnectionRequest(
                null, AssetKind.SERVER, "  ", 22, "root", SshAuthType.PASSWORD, "secret", null, null, null, null));
        assertFalse(res.ok());
    }

    @Test
    void databaseTcpProbeSucceedsAgainstLocalSocket() throws Exception {
        try (var server = new java.net.ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread acceptor = new Thread(() -> {
                try (var ignored = server.accept()) {
                    // accept one connection
                } catch (Exception ignored) {
                }
            }, "tcp-accept");
            acceptor.setDaemon(true);
            acceptor.start();

            TestConnectionResponse res = service.test(new TestConnectionRequest(
                    null, AssetKind.DATABASE, "127.0.0.1", port, null, null, null, null, null, null, null));
            assertTrue(res.ok(), res.message());
            assertTrue(res.message().contains("TCP"));
        }
    }
}
