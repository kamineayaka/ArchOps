package com.archops.conflict.diagnosis;

/**
 * Multi-replica-safe diagnosis job queue. Dedup / bounded enqueue per diagnosis id.
 */
public interface DiagnosisJobQueue {

    /**
     * Enqueue a pending diagnosis job.
     *
     * @return true if newly enqueued; false if already queued/processing (dedup)
     */
    boolean enqueue(String diagnosisId);

    /**
     * Poll next job id, or empty if none.
     */
    java.util.Optional<String> poll();
}
