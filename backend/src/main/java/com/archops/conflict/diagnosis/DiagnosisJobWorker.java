package com.archops.conflict.diagnosis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the diagnosis job queue (Redis in prod / in-memory in tests).
 */
@Component
public class DiagnosisJobWorker {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisJobWorker.class);

    private final DiagnosisJobQueue diagnosisJobQueue;
    private final ConflictDiagnosisService conflictDiagnosisService;

    public DiagnosisJobWorker(
            DiagnosisJobQueue diagnosisJobQueue,
            ConflictDiagnosisService conflictDiagnosisService
    ) {
        this.diagnosisJobQueue = diagnosisJobQueue;
        this.conflictDiagnosisService = conflictDiagnosisService;
    }

    @Scheduled(fixedDelayString = "${archops.diagnosis.poll-interval-ms:200}")
    public void poll() {
        diagnosisJobQueue.poll().ifPresent(id -> {
            try {
                conflictDiagnosisService.processDiagnosisJob(id);
            } catch (Exception ex) {
                log.warn("Diagnosis job {} failed: {}", id, ex.toString());
            }
        });
    }
}
