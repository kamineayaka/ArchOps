package com.archops.common.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis SET NX lock so two control-plane replicas cannot execute the same plan concurrently.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisPlanExecutionLock implements PlanExecutionLock {

    static final String KEY_PREFIX = "archops:plan:exec-lock:";

    private final StringRedisTemplate redis;

    public RedisPlanExecutionLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryLock(String planId, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + planId, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void unlock(String planId) {
        redis.delete(KEY_PREFIX + planId);
    }
}
