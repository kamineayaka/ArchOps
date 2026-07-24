package com.archops.ai.provider.dto;

/**
 * Response for {@code GET /api/ai/model-defaults}. Unknown models return zeros (not 500).
 */
public record ModelDefaultsResponse(String model, int maxOutputTokens, int contextWindow) {

    public static ModelDefaultsResponse empty(String model) {
        return new ModelDefaultsResponse(model == null ? "" : model, 0, 0);
    }

    public boolean hasDefaults() {
        return maxOutputTokens > 0 || contextWindow > 0;
    }
}
