package com.archops.conflict.diagnosis;

import com.archops.conflict.dto.ConflictDiagnosisResponse;

import java.util.List;

/**
 * Rule-engine forks (Must). LLM may only enrich summary text later.
 */
public final class DiagnosisRuleEngine {

    public static final String FIX_ACTUAL_TO_CURATED = "FIX_ACTUAL_TO_CURATED";
    public static final String CHANGE_CURATED_TO_OBSERVED = "CHANGE_CURATED_TO_OBSERVED";
    public static final String RESTORE_HEARTBEAT_CHANNEL = "RESTORE_HEARTBEAT_CHANNEL";

    private DiagnosisRuleEngine() {
    }

    /**
     * Pure 观测空洞: only restore observation channel / heartbeat / verification forks.
     * Never guess a unique physical fix from expired actuals.
     */
    public static RuleResult diagnoseHollow(String curatedHostId, String curatedHostName) {
        ConflictDiagnosisResponse.ForkSuggestion fork = new ConflictDiagnosisResponse.ForkSuggestion(
                RESTORE_HEARTBEAT_CHANNEL,
                "恢复观测通道/心跳/核验",
                "RESTORE_CHANNEL",
                "观测因心跳超时进入空洞，当前无可用实际",
                "先恢复 Host Agent 心跳与状态快照；禁止用过期实际给出唯一落点或继续执行指向旧实际的计划。"
        );
        return new RuleResult(
                "策展要求运行于 " + label(curatedHostId, curatedHostName)
                        + "，但观测已因心跳超时进入空洞（无可用实际）。",
                List.of(fork)
        );
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

        String curatedLabel = label(curatedHostId, curatedHostName);
        String observedLabel = label(observedHostId, observedHostName);
        ConflictDiagnosisResponse.ForkSuggestion fixActual = new ConflictDiagnosisResponse.ForkSuggestion(
                FIX_ACTUAL_TO_CURATED,
                "修实际回策展宿主",
                "FIX_ACTUAL",
                "观测宿主与策展宿主不一致",
                "将容器从实际宿主 " + observedLabel
                        + " 迁回策展宿主 " + curatedLabel
                        + "（纯修现场，跳过草案）。"
        );
        ConflictDiagnosisResponse.ForkSuggestion changeCurated = new ConflictDiagnosisResponse.ForkSuggestion(
                CHANGE_CURATED_TO_OBSERVED,
                "改理想",
                "CHANGE_CURATED",
                "承认实际、更新策展",
                "把策展「运行于」从 " + curatedLabel
                        + " 对齐到当前可用观测宿主 " + observedLabel
                        + "（须经草案逐条确认）。"
        );
        return new RuleResult(
                "策展「运行于」" + curatedLabel
                        + "，观测「运行于」" + observedLabel
                        + "，两侧可用且不等。",
                List.of(fixActual, changeCurated)
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
