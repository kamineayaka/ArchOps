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

import com.archops.asset.domain.SshAuthType;
import com.archops.asset.dto.TestConnectionRequest;
import com.archops.asset.dto.TestConnectionResponse;
import com.archops.terminal.pool.AssetSshDialer;
import java.util.List;
import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetConnectionTestServiceTest {

    private AssetSshDialer dialer;
    private AssetConnectionTestService service;

    @BeforeEach
    void setUp() {
        dialer = mock(AssetSshDialer.class);
        service = new AssetConnectionTestService(dialer);
    }

    @Test
    void testsSavedAssetViaSharedDialer() throws Exception {
        ClientSession session = mock(ClientSession.class);
        when(session.isAuthenticated()).thenReturn(true);
        when(dialer.dial(9L)).thenReturn(session);

        TestConnectionResponse res = service.test(new TestConnectionRequest(
                9L, null, null, null, null, null, null));

        assertTrue(res.ok());
        verify(dialer).dial(9L);
        verify(session).close(false);
    }

    @Test
    void testsEphemeralCredentialsViaDialer() throws Exception {
        ClientSession session = mock(ClientSession.class);
        when(session.isAuthenticated()).thenReturn(true);
        when(dialer.dialEphemeral(anyList(), anyString(), anyInt(), anyString(), any(), anyString(), anyLong()))
                .thenReturn(session);

        TestConnectionResponse res = service.test(new TestConnectionRequest(
                null, "10.0.0.1", 22, "root", SshAuthType.PASSWORD, "secret", List.of()));

        assertTrue(res.ok());
        verify(dialer).dialEphemeral(
                eq(List.of()), eq("10.0.0.1"), eq(22), eq("root"),
                eq(SshAuthType.PASSWORD), eq("secret"), eq(10_000L));
    }

    @Test
    void returnsFailureWhenHostMissing() {
        TestConnectionResponse res = service.test(new TestConnectionRequest(
                null, "  ", 22, "root", SshAuthType.PASSWORD, "secret", null));
        assertFalse(res.ok());
    }
}
