package com.archops.conflict.diagnosis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-process queue used when Redis is not available (e.g. HTTP acceptance tests).
 * Still dedups by diagnosis id; not multi-replica safe — Redis path is preferred in prod.
 */
@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryDiagnosisJobQueue implements DiagnosisJobQueue {

    private final ConcurrentHashMap<String, Boolean> queued = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    @Override
    public boolean enqueue(String diagnosisId) {
        if (queued.putIfAbsent(diagnosisId, Boolean.TRUE) != null) {
            return false;
        }
        queue.offer(diagnosisId);
        return true;
    }

    @Override
    public Optional<String> poll() {
        String id = queue.poll();
        if (id != null) {
            queued.remove(id);
        }
        return Optional.ofNullable(id);
    }
}
