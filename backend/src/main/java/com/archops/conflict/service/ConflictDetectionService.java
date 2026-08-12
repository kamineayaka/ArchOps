package com.archops.conflict.service;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.curated.domain.CuratedFact;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.curated.mapper.CuratedFactMapper;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.domain.ObservedFact;
import com.archops.observed.mapper.ObservedFactMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits conflict warnings when curated ≠ currently available observed on a merge key.
 * Does not wait on diagnosis. Hollow (no usable observed) does not open a both-sides conflict.
 */
@Service
public class ConflictDetectionService {

    private final ConflictCaseMapper conflictCaseMapper;
    private final CuratedFactMapper curatedFactMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final ObservedFactMapper observedFactMapper;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final ObjectMapper objectMapper;

    public ConflictDetectionService(
            ConflictCaseMapper conflictCaseMapper,
            CuratedFactMapper curatedFactMapper,
            CuratedObjectMapper curatedObjectMapper,
            ObservedFactMapper observedFactMapper,
            ConflictDiagnosisService conflictDiagnosisService,
            ObjectMapper objectMapper
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.curatedFactMapper = curatedFactMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.observedFactMapper = observedFactMapper;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.objectMapper = objectMapper;
    }

    /**
     * Reconcile open conflict for merge key (subject + relation) after an observed write.
     */
    @Transactional
    public void reconcileAfterObservedWrite(String subjectId, CuratedRelationType relationType) {
        CuratedFact curated = curatedFactMapper.selectOne(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getSubjectId, subjectId)
                .eq(CuratedFact::getRelationType, relationType));
        ObservedFact observed = observedFactMapper.selectOne(new LambdaQueryWrapper<ObservedFact>()
                .eq(ObservedFact::getSubjectId, subjectId)
                .eq(ObservedFact::getRelationType, relationType));

        // 观测空洞: no usable observed value → do not open a both-sides-available conflict.
        if (curated == null || observed == null) {
            return;
        }

        boolean equal = isEqual(curated, observed);
        ConflictCase open = findOpen(subjectId, relationType);
        if (equal) {
            return;
        }

        Instant now = Instant.now();
        if (open == null) {
            createOpen(subjectId, relationType, curated, observed, now);
            return;
        }

        if (sameObservedSnapshot(open, observed) && Objects.equals(open.getCuratedTargetId(), curated.getTargetId())) {
            return;
        }

        upgradeOpen(open, curated, observed, now);
    }

    @Transactional(readOnly = true)
    public List<ConflictCaseResponse> listOpen() {
        return conflictCaseMapper.selectList(new LambdaQueryWrapper<ConflictCase>()
                        .eq(ConflictCase::getStatus, ConflictStatus.OPEN)
                        .orderByDesc(ConflictCase::getUpdatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConflictCaseResponse getById(String id) {
        ConflictCase row = conflictCaseMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND", "Conflict not found: " + id);
        }
        return toResponse(row);
    }

    @Transactional(readOnly = true)
    public ConflictCaseResponse getOpenByMergeKey(String subjectId, CuratedRelationType relationType) {
        ConflictCase open = findOpen(subjectId, relationType);
        if (open == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND",
                    "No open conflict for merge key subject=" + subjectId + " relation=" + relationType);
        }
        return toResponse(open);
    }

    private void createOpen(
            String subjectId,
            CuratedRelationType relationType,
            CuratedFact curated,
            ObservedFact observed,
            Instant now
    ) {
        ConflictCase created = new ConflictCase();
        created.setId(newId("cnf"));
        created.setSubjectId(subjectId);
        created.setRelationType(relationType);
        created.setStatus(ConflictStatus.OPEN);
        created.setCuratedTargetId(curated.getTargetId());
        created.setObservedAvailability(observed.getAvailability());
        created.setObservedTargetId(observed.getTargetId());
        created.setObservedLineageJson(writeLineage(List.of(lineageStep(observed, now))));
        created.setFirstWarnedAt(now);
        created.setUpdatedAt(now);
        created.setAcknowledged(false);
        created.setAcknowledgedAt(null);
        created.setOwnerUserId(null);
        created.setHandlerUserId(null);
        created.setHandlerAcceptance(HandlerAcceptance.NONE);
        conflictCaseMapper.insert(created);
        // Async diagnosis — never blocks the warning emission.
        conflictDiagnosisService.scheduleAsyncDiagnosis(created.getId());
    }

    private void upgradeOpen(ConflictCase open, CuratedFact curated, ObservedFact observed, Instant now) {
        List<LineageRecord> lineage = readLineage(open.getObservedLineageJson());
        LineageRecord next = lineageStep(observed, now);
        if (lineage.isEmpty() || !sameStep(lineage.get(lineage.size() - 1), next)) {
            lineage.add(next);
        }

        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, open.getId())
                .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                .set(ConflictCase::getObservedLineageJson, writeLineage(lineage))
                .set(ConflictCase::getUpdatedAt, now));
        conflictDiagnosisService.scheduleAsyncDiagnosis(open.getId());
    }

    private ConflictCase findOpen(String subjectId, CuratedRelationType relationType) {
        return conflictCaseMapper.selectOne(new LambdaQueryWrapper<ConflictCase>()
                .eq(ConflictCase::getSubjectId, subjectId)
                .eq(ConflictCase::getRelationType, relationType)
                .eq(ConflictCase::getStatus, ConflictStatus.OPEN));
    }

    private boolean isEqual(CuratedFact curated, ObservedFact observed) {
        if (observed.getAvailability() == ObservedAvailability.ABSENT) {
            return false;
        }
        return Objects.equals(curated.getTargetId(), observed.getTargetId());
    }

    private boolean sameObservedSnapshot(ConflictCase open, ObservedFact observed) {
        return open.getObservedAvailability() == observed.getAvailability()
                && Objects.equals(open.getObservedTargetId(), observed.getTargetId());
    }

    private ConflictCaseResponse toResponse(ConflictCase row) {
        CuratedObject subject = curatedObjectMapper.selectById(row.getSubjectId());
        CuratedObject curatedHost = curatedObjectMapper.selectById(row.getCuratedTargetId());
        CuratedObject observedHost = row.getObservedTargetId() == null
                ? null
                : curatedObjectMapper.selectById(row.getObservedTargetId());

        ConflictCaseResponse.TrackValue curatedValue = ConflictCaseResponse.TrackValue.present(
                curatedHost != null ? curatedHost.getId() : row.getCuratedTargetId(),
                curatedHost != null ? curatedHost.getName() : null
        );
        ConflictCaseResponse.TrackValue observedValue = row.getObservedAvailability() == ObservedAvailability.ABSENT
                ? ConflictCaseResponse.TrackValue.absent()
                : ConflictCaseResponse.TrackValue.present(
                observedHost != null ? observedHost.getId() : row.getObservedTargetId(),
                observedHost != null ? observedHost.getName() : null
        );

        List<ConflictCaseResponse.LineageStep> lineage = readLineage(row.getObservedLineageJson()).stream()
                .map(step -> {
                    String hostName = null;
                    if (step.hostId() != null) {
                        CuratedObject host = curatedObjectMapper.selectById(step.hostId());
                        hostName = host != null ? host.getName() : null;
                    }
                    return new ConflictCaseResponse.LineageStep(
                            step.availability(),
                            step.hostId(),
                            hostName,
                            step.at()
                    );
                })
                .toList();

        return new ConflictCaseResponse(
                row.getId(),
                ConflictCaseResponse.ConflictStatusView.OPEN,
                new ConflictCaseResponse.MergeKey(
                        row.getSubjectId(),
                        row.getRelationType(),
                        row.getRelationType().labelZh()
                ),
                subject != null ? CuratedObjectResponse.from(subject) : null,
                curatedValue,
                observedValue,
                lineage,
                row.getFirstWarnedAt(),
                row.getUpdatedAt(),
                conflictDiagnosisService.statusLabelForConflict(row.getId()),
                new ConflictCaseResponse.Collaboration(
                        Boolean.TRUE.equals(row.getAcknowledged()),
                        row.getAcknowledgedAt(),
                        row.getOwnerUserId(),
                        row.getHandlerUserId(),
                        row.getHandlerAcceptance() == null
                                ? HandlerAcceptance.NONE
                                : row.getHandlerAcceptance()
                )
        );
    }

    private LineageRecord lineageStep(ObservedFact observed, Instant at) {
        return new LineageRecord(observed.getAvailability(), observed.getTargetId(), at);
    }

    private boolean sameStep(LineageRecord a, LineageRecord b) {
        return a.availability() == b.availability() && Objects.equals(a.hostId(), b.hostId());
    }

    private List<LineageRecord> readLineage(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<LineageRecord> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return new ArrayList<>(parsed);
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
    }

    private String writeLineage(List<LineageRecord> lineage) {
        try {
            return objectMapper.writeValueAsString(lineage);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record LineageRecord(
            ObservedAvailability availability,
            String hostId,
            Instant at
    ) {
    }
}
