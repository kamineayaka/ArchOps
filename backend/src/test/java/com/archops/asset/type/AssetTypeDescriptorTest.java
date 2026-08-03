package com.archops.asset.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archops.asset.domain.Asset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetTypeDescriptorTest {

    @Test
    void emitsLowercaseConnectActionMatchingFrontend() {
        AssetTypeDescriptor server = AssetTypeDescriptor.from(stub("SERVER", 22, ConnectAction.TERMINAL));
        assertEquals("SERVER", server.type());
        assertEquals("terminal", server.connectAction());
        assertEquals("ssh", server.authMode());
        assertTrue(server.supportsTest());
        assertFalse(server.showDatabaseName());

        AssetTypeDescriptor database = AssetTypeDescriptor.from(stub("DATABASE", 5432, ConnectAction.QUERY));
        assertEquals("DATABASE", database.type());
        assertEquals("query", database.connectAction());
        assertEquals("password", database.authMode());
        assertTrue(database.showDatabaseName());
        assertTrue(database.supportsTest());

        AssetTypeDescriptor tag = AssetTypeDescriptor.from(stub("TAG", 0, ConnectAction.NONE));
        assertEquals("none", tag.connectAction());
        assertEquals("none", tag.authMode());
        assertFalse(tag.showHost());
        assertFalse(tag.showPort());
    }

    private static AssetTypeHandler stub(String type, int port, ConnectAction action) {
        return new AssetTypeHandler() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public int defaultPort() {
                return port;
            }

            @Override
            public String policyKind() {
                return "GENERIC";
            }

            @Override
            public ConnectAction connectAction() {
                return action;
            }

            @Override
            public Map<String, Object> safeView(Asset asset) {
                return Map.of();
            }
        };
    }
}
