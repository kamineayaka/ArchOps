package com.archops.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archops.ai.provider.domain.ProviderType;
import com.archops.ai.provider.domain.ReasoningEffort;
import com.archops.ai.provider.service.AiProviderService;
import com.archops.ai.runtime.LlmGenerationConfig;
import com.archops.ai.provider.domain.AiProvider;
import org.junit.jupiter.api.Test;

class ReasoningEffortNormalizationTest {

    @Test
    void openaiCompat_normalizesMaxToHigh() {
        assertEquals(
                ReasoningEffort.HIGH,
                AiProviderService.normalizeReasoningEffort(ProviderType.OPENAI_COMPAT, ReasoningEffort.MAX));
    }

    @Test
    void anthropic_keepsMax() {
        assertEquals(
                ReasoningEffort.MAX,
                AiProviderService.normalizeReasoningEffort(ProviderType.ANTHROPIC, ReasoningEffort.MAX));
    }

    @Test
    void nullEffort_defaultsToNone() {
        assertEquals(
                ReasoningEffort.NONE,
                AiProviderService.normalizeReasoningEffort(ProviderType.OPENAI_COMPAT, null));
    }

    @Test
    void generationConfig_disablesWhenEffortNone() {
        AiProvider provider = new AiProvider();
        provider.setMaxOutputTokens(2048);
        provider.setContextWindow(8000);
        provider.setReasoningEnabled(true);
        provider.setReasoningEffort(ReasoningEffort.NONE);

        LlmGenerationConfig config = LlmGenerationConfig.from(provider);
        assertFalse(config.reasoningEnabled());
        assertEquals(ReasoningEffort.NONE, config.reasoningEffort());
        assertEquals(2048, config.effectiveMaxTokens(4096));
        assertTrue(config.contextCharBudget() >= 2000);
    }

    @Test
    void generationConfig_zeroTokensUsesFallback() {
        AiProvider provider = new AiProvider();
        provider.setMaxOutputTokens(0);
        provider.setContextWindow(0);
        provider.setReasoningEnabled(false);
        provider.setReasoningEffort(ReasoningEffort.NONE);

        LlmGenerationConfig config = LlmGenerationConfig.from(provider);
        assertEquals(4096, config.effectiveMaxTokens(4096));
        assertEquals(0, config.contextCharBudget());
    }

    @Test
    void openAiValue_andAnthropicBudget() {
        assertEquals("low", ReasoningEffort.LOW.toOpenAiValue());
        assertEquals("high", ReasoningEffort.XHIGH.toOpenAiValue());
        assertEquals(64_000, ReasoningEffort.MAX.anthropicBudgetTokens());
        assertTrue(ReasoningEffort.MEDIUM.isEnabled());
        assertFalse(ReasoningEffort.NONE.isEnabled());
    }
}
