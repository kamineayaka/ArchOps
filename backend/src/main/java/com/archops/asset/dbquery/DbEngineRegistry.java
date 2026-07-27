package com.archops.asset.dbquery;

import com.archops.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DbEngineRegistry {

    private final List<DbEngine> engines;

    public DbEngineRegistry(List<DbEngine> engines) {
        this.engines = engines;
    }

    public DbEngine resolve(String engineId) {
        String id = engineId == null || engineId.isBlank() ? "postgresql" : engineId.trim().toLowerCase(Locale.ROOT);
        return engines.stream()
                .filter(e -> e.id().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "DB_ENGINE_UNSUPPORTED",
                        "不支持的数据库引擎: " + id + "（首期仅 postgresql）"));
    }
}
