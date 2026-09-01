package com.archops.plan.dispatch;

/**
 * Control-plane adapter: local fake (legacy HTTP tests) or gRPC 代发 (ADR-0045).
 */
public interface ExecutorDispatchPort {

    ExecuteStepResult executeStep(ExecuteStepCommand command);

    default void ensureReady() {
        // Local fake is always ready.
    }
}
