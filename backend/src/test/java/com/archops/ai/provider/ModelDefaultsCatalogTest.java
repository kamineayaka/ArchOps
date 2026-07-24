package com.archops.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.archops.ai.provider.dto.ModelDefaultsResponse;
import com.archops.ai.provider.service.ModelDefaultsCatalog;
import org.junit.jupiter.api.Test;

class ModelDefaultsCatalogTest {

    private final ModelDefaultsCatalog catalog = new ModelDefaultsCatalog();

    @Test
    void knownOpenAiModelHasDefaults() {
        ModelDefaultsResponse d = catalog.defaultsFor("gpt-4o");
        assertThat(d.hasDefaults()).isTrue();
        assertThat(d.maxOutputTokens()).isGreaterThan(0);
        assertThat(d.contextWindow()).isGreaterThan(0);
    }

    @Test
    void datedAnthropicIdMatchesStem() {
        ModelDefaultsResponse d = catalog.defaultsFor("claude-3-5-sonnet-20241022");
        assertThat(d.maxOutputTokens()).isEqualTo(8192);
        assertThat(d.contextWindow()).isEqualTo(200000);
    }

    @Test
    void unknownModelReturnsZerosNotError() {
        ModelDefaultsResponse d = catalog.defaultsFor("totally-unknown-model-xyz");
        assertThat(d.maxOutputTokens()).isZero();
        assertThat(d.contextWindow()).isZero();
        assertThat(d.hasDefaults()).isFalse();
    }

    @Test
    void blankModelReturnsEmpty() {
        ModelDefaultsResponse d = catalog.defaultsFor("  ");
        assertThat(d.hasDefaults()).isFalse();
    }
}
