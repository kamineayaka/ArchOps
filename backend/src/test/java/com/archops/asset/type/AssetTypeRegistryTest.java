package com.archops.asset.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.archops.common.exception.BusinessException;
import com.archops.terminal.pool.AssetSshDialer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetTypeRegistryTest {

    private AssetTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AssetTypeRegistry(List.of(
                new ServerAssetTypeHandler(mock(AssetSshDialer.class)),
                new ClusterAssetTypeHandler(),
                new ServiceAssetTypeHandler(),
                new NetworkAssetTypeHandler(),
                new DatabaseAssetTypeHandler(),
                new K8sAssetTypeHandler(mock(AssetSshDialer.class))));
    }

    @Test
    void discoversServerAndDatabaseHandlers() {
        assertThat(registry.findRequired("SERVER")).isInstanceOf(ServerAssetTypeHandler.class);
        assertThat(registry.findRequired("DATABASE")).isInstanceOf(DatabaseAssetTypeHandler.class);
        assertThat(registry.findRequired("K8S")).isInstanceOf(K8sAssetTypeHandler.class);

        assertThat(registry.find("SERVER")).isPresent();
        assertThat(registry.find("DATABASE")).isPresent();

        assertThat(registry.all())
                .extracting(AssetTypeHandler::type)
                .contains("SERVER", "DATABASE", "CLUSTER", "SERVICE", "NETWORK", "K8S");
    }

    @Test
    void serverDefaultsAndPolicy() {
        AssetTypeHandler server = registry.findRequired("SERVER");
        assertThat(server.defaultPort()).isEqualTo(22);
        assertThat(server.policyKind()).isEqualTo("SSH");
        assertThat(server.connectAction()).isEqualTo(ConnectAction.TERMINAL);
    }

    @Test
    void databaseDefaultsAndConnectAction() {
        AssetTypeHandler database = registry.findRequired("DATABASE");
        assertThat(database.defaultPort()).isEqualTo(5432);
        assertThat(database.policyKind()).isEqualTo("GENERIC");
        assertThat(database.connectAction()).isEqualTo(ConnectAction.QUERY);
    }

    @Test
    void descriptorsExposeConnectAction() {
        assertThat(registry.descriptors())
                .anySatisfy(d -> {
                    assertThat(d.type()).isEqualTo("DATABASE");
                    assertThat(d.connectAction()).isEqualTo("QUERY");
                });
    }

    @Test
    void k8sDefaultsAndConnectAction() {
        AssetTypeHandler k8s = registry.findRequired("K8S");
        assertThat(k8s.defaultPort()).isEqualTo(6443);
        assertThat(k8s.connectAction()).isEqualTo(ConnectAction.PAGE);
    }

    @Test
    void findRequiredThrowsForUnknownType() {
        assertThatThrownBy(() -> registry.findRequired("KAFKA"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("UNKNOWN_ASSET_TYPE");
    }
}
