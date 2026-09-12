package com.archops.conflict.service;

import com.archops.common.exception.BusinessException;
import com.archops.common.json.PersistentJson;
import com.archops.conflict.diagnosis.ConflictDiagnosisService;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.archops.observed.domain.ObservedAvailability;
import com.archops.observed.mapper.IdentityLostMarkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * HTTP / DTO assembly for conflict cases. Detection stays reconcile / hollow / upgrade-void.
 */
@Component
public class ConflictCaseAssembler {

    private static final List<ConflictStatus> ACTIVE = List.of(
            ConflictStatus.OPEN,
            ConflictStatus.PENDING_CLOSE,
            ConflictStatus.SUSPENDED
    );

    private final ConflictCaseMapper conflictCaseMapper;
    private final CuratedObjectMapper curatedObjectMapper;
    private final IdentityLostMarkMapper identityLostMarkMapper;
    private final ConflictDiagnosisService conflictDiagnosisService;
    private final PersistentJson persistentJson;

    public ConflictCaseAssembler(
            ConflictCaseMapper conflictCaseMapper,
            CuratedObjectMapper curatedObjectMapper,
            IdentityLostMarkMapper identityLostMarkMapper,
            ConflictDiagnosisService conflictDiagnosisService,
            PersistentJson persistentJson
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.curatedObjectMapper = curatedObjectMapper;
        this.identityLostMarkMapper = identityLostMarkMapper;
        this.conflictDiagnosisService = conflictDiagnosisService;
        this.persistentJson = persistentJson;
    }

    @Transactional(readOnly = true)
    public List<ConflictCaseResponse> listActive() {
        return conflictCaseMapper.selectList(new LambdaQueryWrapper<ConflictCase>()
                        .in(ConflictCase::getStatus, ACTIVE)
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
    public ConflictCaseResponse getActiveByMergeKey(String subjectId, CuratedRelationType relationType) {
        ConflictCase active = conflictCaseMapper.selectOne(new LambdaQueryWrapper<ConflictCase>()
                .eq(ConflictCase::getSubjectId, subjectId)
                .eq(ConflictCase::getRelationType, relationType)
                .in(ConflictCase::getStatus, ACTIVE));
        if (active == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND",
                    "No active conflict for merge key subject=" + subjectId + " relation=" + relationType);
        }
        return toResponse(active);
    }

    public ConflictCaseResponse toResponse(ConflictCase row) {
        CuratedObject subject = curatedObjectMapper.selectById(row.getSubjectId());
        CuratedObject curatedHost = curatedObjectMapper.selectById(row.getCuratedTargetId());
        CuratedObject observedHost = row.getObservedTargetId() == null
                ? null
                : curatedObjectMapper.selectById(row.getObservedTargetId());

        ConflictCaseResponse.TrackValue curatedValue = ConflictCaseResponse.TrackValue.present(
                curatedHost != null ? curatedHost.getId() : row.getCuratedTargetId(),
                curatedHost != null ? curatedHost.getName() : null
        );
        boolean hollow = row.getStatus() == ConflictStatus.SUSPENDED;
        boolean identityLost = identityLostMarkMapper.selectById(row.getSubjectId()) != null;
        ConflictCaseResponse.TrackValue observedValue = observedTrackValue(
                hollow, identityLost, row.getObservedAvailability(), observedHost, row.getObservedTargetId());

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
                statusView(row.getStatus()),
                mergeKey(row.getSubjectId(), row.getRelationType()),
                subject != null ? CuratedObjectResponse.from(subject) : null,
                curatedValue,
                observedValue,
                lineage,
                row.getFirstWarnedAt(),
                row.getUpdatedAt(),
                row.getPendingCloseAt(),
                row.getClosedAt(),
                row.getSuspendedAt(),
                row.getStatus() == ConflictStatus.PENDING_CLOSE,
                hollow,
                identityLost,
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

    static ConflictCaseResponse.TrackValue observedTrackValue(
            boolean hollow,
            boolean identityLost,
            ObservedAvailability availability,
            CuratedObject observedHost,
            String observedTargetId
    ) {
        if (hollow) {
            // Do not present stale snapshot as trustworthy 实际 during 空洞挂起.
            return ConflictCaseResponse.TrackValue.hollow();
        }
        if (identityLost) {
            // 身份失联 is not 观测空洞: keep OPEN, do not show residual observed_fact as 实际.
            return ConflictCaseResponse.TrackValue.identityLost();
        }
        if (availability == ObservedAvailability.ABSENT) {
            return ConflictCaseResponse.TrackValue.absent();
        }
        return ConflictCaseResponse.TrackValue.present(
                observedHost != null ? observedHost.getId() : observedTargetId,
                observedHost != null ? observedHost.getName() : null
        );
    }

    static ConflictCaseResponse.ConflictStatusView statusView(ConflictStatus status) {
        return switch (status) {
            case OPEN -> ConflictCaseResponse.ConflictStatusView.OPEN;
            case PENDING_CLOSE -> ConflictCaseResponse.ConflictStatusView.PENDING_CLOSE;
            case CLOSED -> ConflictCaseResponse.ConflictStatusView.CLOSED;
            case SUSPENDED -> ConflictCaseResponse.ConflictStatusView.SUSPENDED;
        };
    }

    static ConflictCaseResponse.MergeKey mergeKey(String subjectId, CuratedRelationType relationType) {
        return new ConflictCaseResponse.MergeKey(subjectId, relationType, relationType.labelZh());
    }

    private List<LineageRecord> readLineage(String json) {
        return persistentJson.read(json, new TypeReference<>() {
        }, List.of());
    }

    private record LineageRecord(
            ObservedAvailability availability,
            String hostId,
            Instant at
    ) {
    }
}
