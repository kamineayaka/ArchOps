package com.archops.conflict.diagnosis;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs diagnosis off the request thread so warnings stay non-blocking.
 */
@Component
public class DiagnosisAsyncRunner {

    private final ConflictDiagnosisService conflictDiagnosisService;

    public DiagnosisAsyncRunner(ConflictDiagnosisService conflictDiagnosisService) {
        this.conflictDiagnosisService = conflictDiagnosisService;
    }

    @Async
    public void runAsync(String diagnosisId) {
        conflictDiagnosisService.processDiagnosisJob(diagnosisId);
    }
}
