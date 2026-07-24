package com.archops.tools.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.tools.AgentTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAssetsToolTest {

    @Mock
    private AssetService assetService;

    @InjectMocks
    private ListAssetsTool tool;

    @Test
    void filtersByKindAndScope() {
        when(assetService.list()).thenReturn(List.of(
                asset(1L, "db1", AssetKind.DATABASE),
                asset(2L, "redis1", AssetKind.REDIS),
                asset(3L, "srv", AssetKind.SERVER)));

        var ctx = new AgentTool.ExecutionContext(1L, "ops", 10L, List.of(1L, 2L), null);
        String out = tool.execute(Map.of("kind", "DATABASE"), ctx);

        assertThat(out).contains("id=1").contains("DATABASE").doesNotContain("redis1").doesNotContain("id=3");
    }

    private static AssetResponse asset(Long id, String name, AssetKind kind) {
        return new AssetResponse(
                id, name, kind, "127.0.0.1", 22, null, null, null, true, false, List.of(), null, null);
    }
}
