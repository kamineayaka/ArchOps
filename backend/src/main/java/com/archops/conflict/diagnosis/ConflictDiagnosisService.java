package com.archops.conflict.diagnosis;

import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictDiagnosis;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.DiagnosisSource;
import com.archops.conflict.domain.DiagnosisStatus;
import com.archops.conflict.dto.ConflictDiagnosisResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.conflict.mapper.ConflictDiagnosisMapper;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.mapper.IdentityLostMarkMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConflictDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(ConflictDiagnosisService.class);

    private final ConflictDiagnosisMapper diagnosisMapper;
    private final ConflictCaseMapper conflictCaseMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final IdentityLostMarkMapper identityLostMarkMapper;
    private final DiagnosisJobQueue diagnosisJobQueue;
    private final DiagnosisAsyncRunner diagnosisAsyncRunner;
    private final ObjectMapper objectMapper;

    public ConflictDiagnosisService(
            ConflictDiagnosisMapper diagnosisMapper,
            ConflictCaseMapper conflictCaseMapper,
            CuratedObjectMapper curatedObjectMapper,
            IdentityLostMarkMapper identityLostMarkMapper,
            DiagnosisJobQueue diagnosisJobQueue,
            ObjectMapper objectMapper,
            @Lazy DiagnosisAsyncRunner diagnosisAsyncRunner
    ) {
        this.diagnosisMapper = diagnosisMapper;
        this.conflictCaseMapper = conflictCaseMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.identityLostMarkMapper = identityLostMarkMapper;
        this.diagnosisJobQueue = diagnosisJobQueue;
        this.objectMapper = objectMapper;
        this.diagnosisAsyncRunner = diagnosisAsyncRunner;
    }

    /**
     * Called from conflict create/upgrade in the same transaction.
     * Inserts PENDING row and enqueues after commit (warning is never blocked).
     */
    @Transactional
    public void scheduleAsyncDiagnosis(String conflictId) {
        Instant now = Instant.now();
        diagnosisMapper.update(null, new LambdaUpdateWrapper<ConflictDiagnosis>()
                .eq(ConflictDiagnosis::getConflictId, conflictId)
                .in(ConflictDiagnosis::getStatus, DiagnosisStatus.PENDING, DiagnosisStatus.READY)
                .set(ConflictDiagnosis::getStatus, DiagnosisStatus.STALE));

        ConflictDiagnosis pending = new ConflictDiagnosis();
        pending.setId("diag-" + UUID.randomUUID());
        pending.setConflictId(conflictId);
        pending.setStatus(DiagnosisStatus.PENDING);
        pending.setForksJson("[]");
        pending.setCreatedAt(now);
        diagnosisMapper.insert(pending);

        String diagnosisId = pending.getId();
        Runnable enqueue = () -> {
            try {
                boolean enqueued = diagnosisJobQueue.enqueue(diagnosisId);
                // Also kick async processing so warnings never wait on the poller,
                // while Redis queue still provides multi-replica dedup/handoff.
                if (enqueued) {
                    diagnosisAsyncRunner.runAsync(diagnosisId);
                }
            } catch (Exception ex) {
                log.warn("Failed to enqueue diagnosis {}: {}", diagnosisId, ex.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue.run();
                }
            });
        } else {
            enqueue.run();
        }
    }

    @Transactional
    public void processDiagnosisJob(String diagnosisId) {
        ConflictDiagnosis row = diagnosisMapper.selectById(diagnosisId);
        if (row == null || row.getStatus() != DiagnosisStatus.PENDING) {
            return;
        }
        ConflictCase conflict = conflictCaseMapper.selectById(row.getConflictId());
        if (conflict == null) {
            markFailed(row, "Conflict missing");
            return;
        }

        CuratedObject curatedHost = curatedObjectMapper.selectById(conflict.getCuratedTargetId());
        CuratedObject observedHost = conflict.getObservedTargetId() == null
                ? null
                : curatedObjectMapper.selectById(conflict.getObservedTargetId());

        DiagnosisRuleEngine.RuleResult rules = rulesFor(conflict, curatedHost, observedHost);

        // ADR-0044: control plane holds no model keys. LLM enrichment lives on the AI 编排层.
        // Until that process exists, 规则分叉兜底 (ADR-0041) remains the only in-process source.
        DiagnosisSource source = DiagnosisSource.RULES;
        String summary = rules.summary();

        Instant now = Instant.now();
        diagnosisMapper.update(null, new LambdaUpdateWrapper<ConflictDiagnosis>()
                .eq(ConflictDiagnosis::getId, row.getId())
                .eq(ConflictDiagnosis::getStatus, DiagnosisStatus.PENDING)
                .set(ConflictDiagnosis::getStatus, DiagnosisStatus.READY)
                .set(ConflictDiagnosis::getSource, source)
                .set(ConflictDiagnosis::getSummary, summary)
                .set(ConflictDiagnosis::getForksJson, writeForks(rules.forks()))
                .set(ConflictDiagnosis::getCompletedAt, now)
                .set(ConflictDiagnosis::getErrorMessage, null));
    }

    @Transactional(readOnly = true)
    public ConflictDiagnosisResponse latestForConflict(String conflictId) {
        ConflictDiagnosis row = diagnosisMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConflictDiagnosis>()
                        .eq(ConflictDiagnosis::getConflictId, conflictId)
                        .ne(ConflictDiagnosis::getStatus, DiagnosisStatus.STALE)
                        .orderByDesc(ConflictDiagnosis::getCreatedAt)
                        .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        return toResponse(row);
    }

    @Transactional(readOnly = true)
    public String statusLabelForConflict(String conflictId) {
        ConflictDiagnosisResponse latest = latestForConflict(conflictId);
        if (latest == null) {
            return "NOT_STARTED";
        }
        return latest.status().name();
    }

    public ConflictDiagnosisResponse toResponse(ConflictDiagnosis row) {
        return new ConflictDiagnosisResponse(
                row.getId(),
                row.getConflictId(),
                row.getStatus(),
                row.getSource(),
                row.getSummary(),
                readForks(row.getForksJson()),
                row.getCreatedAt(),
                row.getCompletedAt(),
                row.getErrorMessage()
        );
    }

    private DiagnosisRuleEngine.RuleResult rulesFor(
            ConflictCase conflict,
            CuratedObject curatedHost,
            CuratedObject observedHost
    ) {
        if (conflict.getStatus() == ConflictStatus.SUSPENDED) {
            return DiagnosisRuleEngine.diagnoseHollow(
                    conflict.getCuratedTargetId(),
                    curatedHost != null ? curatedHost.getName() : null
            );
        }
        if (identityLostMarkMapper.selectById(conflict.getSubjectId()) != null) {
            return DiagnosisRuleEngine.diagnoseIdentityLost();
        }
        return DiagnosisRuleEngine.diagnoseRunsOnMismatch(
                conflict.getCuratedTargetId(),
                curatedHost != null ? curatedHost.getName() : null,
                conflict.getObservedAvailability() == null
                        ? ObservedAvailability.PRESENT.name()
                        : conflict.getObservedAvailability().name(),
                conflict.getObservedTargetId(),
                observedHost != null ? observedHost.getName() : null
        );
    }

    private void markFailed(ConflictDiagnosis row, String message) {
        diagnosisMapper.update(null, new LambdaUpdateWrapper<ConflictDiagnosis>()
                .eq(ConflictDiagnosis::getId, row.getId())
                .set(ConflictDiagnosis::getStatus, DiagnosisStatus.FAILED)
                .set(ConflictDiagnosis::getErrorMessage, message)
                .set(ConflictDiagnosis::getCompletedAt, Instant.now())
                .set(ConflictDiagnosis::getSource, DiagnosisSource.RULES));
    }

    private String writeForks(List<ConflictDiagnosisResponse.ForkSuggestion> forks) {
        try {
            return objectMapper.writeValueAsString(forks);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<ConflictDiagnosisResponse.ForkSuggestion> readForks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
