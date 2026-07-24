package com.archops.ai.provider.domain;

/**
 * OpsKat-aligned reasoning effort levels.
 * {@code MAX} is Anthropic-only; OpenAI-compat providers normalize it to {@code HIGH}.
 */
public enum ReasoningEffort {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX;

    public boolean isEnabled() {
        return this != NONE;
    }

    /** OpenAI-compatible APIs typically accept low|medium|high. */
    public String toOpenAiValue() {
        return switch (this) {
            case NONE -> null;
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH, XHIGH, MAX -> "high";
        };
    }

    public int anthropicBudgetTokens() {
        return switch (this) {
            case NONE -> 0;
            case LOW -> 1_024;
            case MEDIUM -> 4_096;
            case HIGH -> 10_000;
            case XHIGH -> 32_000;
            case MAX -> 64_000;
        };
    }
}
