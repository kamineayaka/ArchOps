package com.archops.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "archops.ai")
public class AiEgressProperties {

    /**
     * When false or base-url blank, diagnosis uses rules only (Must path).
     */
    private boolean enabled = false;
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private List<String> allowlistHosts = new ArrayList<>(List.of(
            "api.openai.com",
            "api.deepseek.com"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getAllowlistHosts() {
        return allowlistHosts;
    }

    public void setAllowlistHosts(List<String> allowlistHosts) {
        this.allowlistHosts = allowlistHosts;
    }
}
