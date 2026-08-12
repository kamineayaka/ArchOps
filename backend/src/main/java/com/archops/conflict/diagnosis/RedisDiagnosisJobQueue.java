package com.archops.conflict.diagnosis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis list + SET NX dedup for multi-replica diagnosis scheduling.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisDiagnosisJobQueue implements DiagnosisJobQueue {

    static final String QUEUE_KEY = "archops:diagnosis:queue";
    static final String DEDUP_PREFIX = "archops:diagnosis:queued:";

    private final StringRedisTemplate redis;

    public RedisDiagnosisJobQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean enqueue(String diagnosisId) {
        Boolean first = redis.opsForValue().setIfAbsent(DEDUP_PREFIX + diagnosisId, "1", Duration.ofMinutes(30));
        if (!Boolean.TRUE.equals(first)) {
            return false;
        }
        redis.opsForList().leftPush(QUEUE_KEY, diagnosisId);
        // Bound queue length roughly (drop oldest beyond cap).
        Long size = redis.opsForList().size(QUEUE_KEY);
        if (size != null && size > 10_000) {
            redis.opsForList().rightPop(QUEUE_KEY);
        }
        return true;
    }

    @Override
    public Optional<String> poll() {
        String id = redis.opsForList().rightPop(QUEUE_KEY);
        if (id != null) {
            redis.delete(DEDUP_PREFIX + id);
        }
        return Optional.ofNullable(id);
    }
}
