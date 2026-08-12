package com.archops.common.lock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis plan mutex keying (ticket 08) — multi-replica critical section.
 */
class RedisPlanExecutionLockTest {

    @Test
    @SuppressWarnings("unchecked")
    void tryLockUsesSetIfAbsentWithPlanKeyPrefix() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("archops:plan:exec-lock:plan-1"), eq("1"), eq(Duration.ofMinutes(5))))
                .thenReturn(true);

        RedisPlanExecutionLock lock = new RedisPlanExecutionLock(redis);
        assertThat(lock.tryLock("plan-1", Duration.ofMinutes(5))).isTrue();
        verify(values).setIfAbsent("archops:plan:exec-lock:plan-1", "1", Duration.ofMinutes(5));
    }

    @Test
    void unlockDeletesPlanKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisPlanExecutionLock lock = new RedisPlanExecutionLock(redis);
        lock.unlock("plan-9");
        verify(redis).delete("archops:plan:exec-lock:plan-9");
    }
}
