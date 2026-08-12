package com.archops.common.lock;

import java.time.Duration;

/**
 * Mutual exclusion for a single active plan's execution critical section (multi-replica safe via Redis).
 */
public interface PlanExecutionLock {

    boolean tryLock(String planId, Duration ttl);

    void unlock(String planId);
}
