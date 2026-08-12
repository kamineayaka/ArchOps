package com.archops.common.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process plan mutex when Redis is unavailable (HTTP acceptance). Prod uses {@link RedisPlanExecutionLock}.
 */
@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryPlanExecutionLock implements PlanExecutionLock {

    private final ConcurrentHashMap<String, Long> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String planId, Duration ttl) {
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        Long previous = locks.putIfAbsent(planId, expireAt);
        if (previous == null) {
            return true;
        }
        if (previous < System.currentTimeMillis()) {
            return locks.replace(planId, previous, expireAt);
        }
        return false;
    }

    @Override
    public void unlock(String planId) {
        locks.remove(planId);
    }
}
