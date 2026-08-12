package com.archops.conflict.diagnosis;

import com.archops.conflict.dto.ConflictDiagnosisResponse;

import java.util.List;

/**
 * Rule-engine forks (Must). LLM may only enrich summary text later.
 */
public final class DiagnosisRuleEngine {

    public static final String FIX_ACTUAL_TO_CURATED = "FIX_ACTUAL_TO_CURATED";

    private DiagnosisRuleEngine() {
    }

    public static RuleResult diagnoseRunsOnMismatch(
            String curatedHostId,
            String curatedHostName,
            String observedAvailability,
            String observedHostId,
            String observedHostName
    ) {
        if ("ABSENT".equals(observedAvailability)) {
            ConflictDiagnosisResponse.ForkSuggestion fork = new ConflictDiagnosisResponse.ForkSuggestion(
                    "RESTORE_OBSERVATION_OR_RECREATE",
                    "恢复观测或核验对象存在性",
                    "RESTORE_CHANNEL",
                    "观测断言对象不存在（观测消失），与策展应存在/应运行于不一致",
                    "先核验标签与观测通道；禁止用过期实际给出唯一落点。纯修现场前须确认对象仍可调度。"
            );
            return new RuleResult(
                    "策展要求运行于 " + label(curatedHostId, curatedHostName)
                            + "，观测为不存在（ABSENT）。",
                    List.of(fork)
            );
        }

        ConflictDiagnosisResponse.ForkSuggestion fork = new ConflictDiagnosisResponse.ForkSuggestion(
                FIX_ACTUAL_TO_CURATED,
                "修实际回策展宿主",
                "FIX_ACTUAL",
                "观测宿主与策展宿主不一致",
                "将容器从实际宿主 " + label(observedHostId, observedHostName)
                        + " 迁回策展宿主 " + label(curatedHostId, curatedHostName)
                        + "（纯修现场，跳过草案）。"
        );
        return new RuleResult(
                "策展「运行于」" + label(curatedHostId, curatedHostName)
                        + "，观测「运行于」" + label(observedHostId, observedHostName)
                        + "，两侧可用且不等。",
                List.of(fork)
        );
    }

    private static String label(String id, String name) {
        if (name == null || name.isBlank()) {
            return id;
        }
        return name + " (" + id + ")";
    }

    public record RuleResult(
            String summary,
            List<ConflictDiagnosisResponse.ForkSuggestion> forks
    ) {
    }
}
