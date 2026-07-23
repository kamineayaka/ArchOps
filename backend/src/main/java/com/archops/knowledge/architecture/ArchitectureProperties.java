package com.archops.knowledge.architecture;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archops.architecture")
public class ArchitectureProperties {

    private AutoMerge autoMerge = new AutoMerge();
    private int contextMaxChars = 4000;

    public AutoMerge getAutoMerge() {
        return autoMerge;
    }

    public void setAutoMerge(AutoMerge autoMerge) {
        this.autoMerge = autoMerge != null ? autoMerge : new AutoMerge();
    }

    public int getContextMaxChars() {
        return contextMaxChars;
    }

    public void setContextMaxChars(int contextMaxChars) {
        this.contextMaxChars = contextMaxChars;
    }

    public static class AutoMerge {
        private boolean enabled = false;
        private List<String> allowedFactTypes = new ArrayList<>(List.of("ROLE", "LABEL"));
        private double minConfidence = 0.9;
        private boolean requireCommand = true;
        private boolean requireStdoutHash = true;
        private boolean requireAssetId = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedFactTypes() {
            return allowedFactTypes;
        }

        public void setAllowedFactTypes(List<String> allowedFactTypes) {
            this.allowedFactTypes = allowedFactTypes != null
                    ? new ArrayList<>(allowedFactTypes)
                    : new ArrayList<>(List.of("ROLE", "LABEL"));
        }

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }

        public boolean isRequireCommand() {
            return requireCommand;
        }

        public void setRequireCommand(boolean requireCommand) {
            this.requireCommand = requireCommand;
        }

        public boolean isRequireStdoutHash() {
            return requireStdoutHash;
        }

        public void setRequireStdoutHash(boolean requireStdoutHash) {
            this.requireStdoutHash = requireStdoutHash;
        }

        public boolean isRequireAssetId() {
            return requireAssetId;
        }

        public void setRequireAssetId(boolean requireAssetId) {
            this.requireAssetId = requireAssetId;
        }
    }
}
