package com.archops.ai.provider.service;

import com.archops.ai.provider.dto.AiModelInfo;
import com.archops.ai.provider.dto.ModelDefaultsResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Built-in catalog of common chat model defaults (OpsKat GetModelDefaults analogue).
 * Exact id match first, then longest prefix / stem match for dated Anthropic ids.
 */
@Component
public class ModelDefaultsCatalog {

    private final Map<String, AiModelInfo> byId;

    public ModelDefaultsCatalog() {
        Map<String, AiModelInfo> map = new LinkedHashMap<>();
        // OpenAI / compat
        put(map, "gpt-4o", 16384, 128000);
        put(map, "gpt-4o-mini", 16384, 128000);
        put(map, "gpt-4.1", 32768, 1047576);
        put(map, "gpt-4.1-mini", 32768, 1047576);
        put(map, "gpt-4.1-nano", 32768, 1047576);
        put(map, "gpt-4-turbo", 4096, 128000);
        put(map, "gpt-4", 8192, 8192);
        put(map, "gpt-3.5-turbo", 4096, 16385);
        put(map, "o1", 100000, 200000);
        put(map, "o1-mini", 65536, 128000);
        put(map, "o1-preview", 32768, 128000);
        put(map, "o3", 100000, 200000);
        put(map, "o3-mini", 100000, 200000);
        put(map, "o4-mini", 100000, 200000);
        // Anthropic
        put(map, "claude-opus-4", 32000, 200000);
        put(map, "claude-sonnet-4", 64000, 200000);
        put(map, "claude-3-7-sonnet", 64000, 200000);
        put(map, "claude-3-5-sonnet", 8192, 200000);
        put(map, "claude-3-5-haiku", 8192, 200000);
        put(map, "claude-3-opus", 4096, 200000);
        put(map, "claude-3-sonnet", 4096, 200000);
        put(map, "claude-3-haiku", 4096, 200000);
        // Popular gateways
        put(map, "deepseek-chat", 8192, 64000);
        put(map, "deepseek-reasoner", 8192, 64000);
        put(map, "qwen-plus", 8192, 131072);
        put(map, "qwen-max", 8192, 32768);
        put(map, "qwen-turbo", 8192, 131072);
        this.byId = Map.copyOf(map);
    }

    public Optional<AiModelInfo> find(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        String key = modelId.trim().toLowerCase(Locale.ROOT);
        AiModelInfo exact = byId.get(key);
        if (exact != null) {
            return Optional.of(new AiModelInfo(modelId.trim(), exact.maxOutputTokens(), exact.contextWindow()));
        }
        // Dated Anthropic / vendor suffixes: claude-3-5-sonnet-20241022 → claude-3-5-sonnet
        String bestStem = null;
        AiModelInfo best = null;
        for (Map.Entry<String, AiModelInfo> e : byId.entrySet()) {
            String stem = e.getKey();
            if (key.equals(stem) || key.startsWith(stem + "-") || key.startsWith(stem + "@")) {
                if (bestStem == null || stem.length() > bestStem.length()) {
                    bestStem = stem;
                    best = e.getValue();
                }
            }
        }
        if (best != null) {
            return Optional.of(new AiModelInfo(modelId.trim(), best.maxOutputTokens(), best.contextWindow()));
        }
        return Optional.empty();
    }

    public ModelDefaultsResponse defaultsFor(String modelId) {
        return find(modelId)
                .map(info -> new ModelDefaultsResponse(
                        info.id(),
                        info.maxOutputTokens() != null ? info.maxOutputTokens() : 0,
                        info.contextWindow() != null ? info.contextWindow() : 0))
                .orElseGet(() -> ModelDefaultsResponse.empty(modelId == null ? "" : modelId.trim()));
    }

    public AiModelInfo enrich(String modelId) {
        return find(modelId).orElseGet(() -> AiModelInfo.of(modelId));
    }

    private static void put(Map<String, AiModelInfo> map, String id, int maxOut, int context) {
        map.put(id.toLowerCase(Locale.ROOT), AiModelInfo.of(id, maxOut, context));
    }
}
