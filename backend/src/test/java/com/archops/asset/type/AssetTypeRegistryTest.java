package com.archops.asset.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archops.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetTypeRegistryTest {

    private AssetTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AssetTypeRegistry(List.of(
                new ServerAssetTypeHandler(),
                new ClusterAssetTypeHandler(),
                new ServiceAssetTypeHandler(),
                new NetworkAssetTypeHandler(),
                new DatabaseAssetTypeHandler()));
    }

    @Test
    void discoversServerAndDatabaseHandlers() {
        assertThat(registry.findRequired("SERVER")).isInstanceOf(ServerAssetTypeHandler.class);
        assertThat(registry.findRequired("DATABASE")).isInstanceOf(DatabaseAssetTypeHandler.class);

        assertThat(registry.find("SERVER")).isPresent();
        assertThat(registry.find("DATABASE")).isPresent();

        assertThat(registry.all())
                .extracting(AssetTypeHandler::type)
                .contains("SERVER", "DATABASE", "CLUSTER", "SERVICE", "NETWORK");
    }

    @Test
    void serverDefaultsAndPolicy() {
        AssetTypeHandler server = registry.findRequired("SERVER");
        assertThat(server.defaultPort()).isEqualTo(22);
        assertThat(server.policyKind()).isEqualTo("SSH");
    }

    @Test
    void databaseStubDefaults() {
        AssetTypeHandler database = registry.findRequired("DATABASE");
        assertThat(database.defaultPort()).isEqualTo(5432);
        assertThat(database.policyKind()).isEqualTo("GENERIC");
    }

    @Test
    void findRequiredThrowsForUnknownType() {
        assertThatThrownBy(() -> registry.findRequired("KAFKA"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("UNKNOWN_ASSET_TYPE");
    }
}
