package com.archops.conflict.domain;

public enum DiagnosisSource {
    RULES,
    /** Stored by pre-ADR-0044 in-process LLM enrich; control plane no longer writes this. */
    RULES_WITH_LLM,
    /** Stored when in-process egress was configured but failed; control plane no longer writes this. */
    RULES_LLM_FALLBACK
}
