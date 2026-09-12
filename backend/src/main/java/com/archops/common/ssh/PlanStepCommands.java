package com.archops.common.ssh;

import com.archops.plan.domain.PlanStepAction;

import java.util.Map;

/**
 * Frozen-plan SSH command lines. Shared by control-plane logs and the 执行引擎.
 */
public final class PlanStepCommands {

    private PlanStepCommands() {
    }

    public static String command(String action, Map<String, String> params, String hostId) {
        return command(PlanStepAction.parse(action), params, hostId);
    }

    public static String command(PlanStepAction action, Map<String, String> params, String hostId) {
        Map<String, String> safe = params == null ? Map.of() : params;
        return switch (action) {
            case SSH_PRECHECK -> "archops-precheck --host " + hostId;
            case MIGRATE_CONTAINER -> "archops-migrate --from " + safe.getOrDefault("fromHostId", "")
                    + " --to " + safe.getOrDefault("toHostId", "")
                    + " --subject " + safe.getOrDefault("subjectId", "");
            case REFRESH_OBSERVATION -> "archops-refresh-observation --subject "
                    + safe.getOrDefault("subjectId", "")
                    + " --host " + hostId;
        };
    }
}
