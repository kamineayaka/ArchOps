package com.archops.ai.runtime;

import com.archops.ai.provider.domain.AiProvider;
import com.archops.ai.provider.domain.ReasoningEffort;

/**
 * Generation knobs resolved from an {@link AiProvider} for chat completions.
 * {@code maxOutputTokens}/{@code contextWindow} of 0 mean "use runtime default".
 */
public record LlmGenerationConfig(
        int maxOutputTokens,
        int contextWindow,
        boolean reasoningEnabled,
        ReasoningEffort reasoningEffort) {

    public static LlmGenerationConfig from(AiProvider provider) {
        ReasoningEffort effort = provider.getReasoningEffort() != null
                ? provider.getReasoningEffort()
                : ReasoningEffort.NONE;
        boolean enabled = provider.isReasoningEnabled() && effort.isEnabled();
        return new LlmGenerationConfig(
                Math.max(0, provider.getMaxOutputTokens()),
                Math.max(0, provider.getContextWindow()),
                enabled,
                enabled ? effort : ReasoningEffort.NONE);
    }

    public int effectiveMaxTokens(int fallback) {
        return maxOutputTokens > 0 ? maxOutputTokens : fallback;
    }

    /** Approximate character budget for assembled system context (≈ 3 chars/token). */
    public int contextCharBudget() {
        if (contextWindow <= 0) {
            return 0;
        }
        return Math.max(2_000, contextWindow * 3);
    }
}
