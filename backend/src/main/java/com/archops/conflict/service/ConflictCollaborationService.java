package com.archops.conflict.service;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictEventType;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.dto.OpenOperationPlanResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.curated.domain.CuratedFact;
import com.archops.observed.domain.ObservedFact;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conflict collaboration: claim / ack / assign / accept / reject / transfer / confirm-close.
 */
@Service
public class ConflictCollaborationService {

    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictDetectionService conflictDetectionService;
    private final ConflictCaseAssembler conflictCaseAssembler;
    private final ConflictEventService conflictEventService;
    private final TransactionTemplate requiresNewTx;

    public ConflictCollaborationService(
            ConflictCaseMapper conflictCaseMapper,
            ConflictDetectionService conflictDetectionService,
            ConflictCaseAssembler conflictCaseAssembler,
            ConflictEventService conflictEventService,
            PlatformTransactionManager transactionManager
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDetectionService = conflictDetectionService;
        this.conflictCaseAssembler = conflictCaseAssembler;
        this.conflictEventService = conflictEventService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 转让不再写入处理人.
     */
    @Transactional(readOnly = true)
    public ConflictCaseResponse transferHandler(String conflictId) {
        requireOpen(conflictId);
        return conflictCaseAssembler.getById(conflictId);
    }

    /**
     * 开计划不再以处理人门禁决定，响应不带处理人 id.
     */
    @Transactional(readOnly = true)
    public OpenOperationPlanResponse openOperationPlan(String conflictId) {
        requireOpen(conflictId);
        return new OpenOperationPlanResponse(
                conflictId,
                "OPEN_INTENT_ACCEPTED",
                "Operation plan intent accepted"
        );
    }

    /**
     * Confirm close while tracks remain equal. Race drift fails without closing.
     */
    @Transactional
    public ConflictCaseResponse confirmClose(String conflictId) {
        ConflictCase row = conflictCaseMapper.selectById(conflictId);
        if (row == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND", "Conflict not found: " + conflictId);
        }
        if (row.getStatus() != ConflictStatus.PENDING_CLOSE) {
            throw new BusinessException("CONFLICT_NOT_PENDING_CLOSE",
                    "Only 待确认关闭 conflicts can be confirmed closed");
        }

        ConflictDetectionService.TrackPair tracks = conflictDetectionService.currentTracks(row);
        CuratedFact curated = tracks.curated();
        ObservedFact observed = tracks.observed();
        boolean equal = curated != null && observed != null
                && conflictDetectionService.tracksCurrentlyEqual(row);

        if (!equal) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("message", "Tracks no longer equal; refresh observation and re-check before close");
            detail.put("curatedTargetId", curated == null ? null : curated.getTargetId());
            detail.put("observedTargetId", observed == null ? null : observed.getTargetId());
            detail.put("observedAvailability", observed == null ? null : observed.getAvailability().name());

            // Commit reopen + audit before throwing so the failure is durable (not rolled back).
            requiresNewTx.executeWithoutResult(status -> {
                Instant now = Instant.now();
                if (curated != null && observed != null) {
                    conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                            .eq(ConflictCase::getId, conflictId)
                            .eq(ConflictCase::getStatus, ConflictStatus.PENDING_CLOSE)
                            .set(ConflictCase::getStatus, ConflictStatus.OPEN)
                            .set(ConflictCase::getCuratedTargetId, curated.getTargetId())
                            .set(ConflictCase::getObservedAvailability, observed.getAvailability())
                            .set(ConflictCase::getObservedTargetId, observed.getTargetId())
                            .set(ConflictCase::getPendingCloseAt, null)
                            .set(ConflictCase::getUpdatedAt, now));
                }
                conflictEventService.append(conflictId, ConflictEventType.CONFIRM_FAILED, null, detail);
            });
            throw new BusinessException("CONFLICT_NOT_ALIGNED",
                    "策展与观测已不再相等，请刷新观测后重试；冲突未关闭");
        }

        Instant now = Instant.now();
        int updated = conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, conflictId)
                .eq(ConflictCase::getStatus, ConflictStatus.PENDING_CLOSE)
                .set(ConflictCase::getStatus, ConflictStatus.CLOSED)
                .set(ConflictCase::getClosedAt, now)
                .set(ConflictCase::getUpdatedAt, now));
        if (updated != 1) {
            throw new BusinessException("CONFLICT_NOT_PENDING_CLOSE",
                    "Conflict left 待确认关闭 before confirm completed");
        }
        conflictEventService.append(conflictId, ConflictEventType.CLOSED, null, Map.of(
                "curatedTargetId", curated.getTargetId(),
                "observedTargetId", observed.getTargetId()
        ));
        return conflictCaseAssembler.getById(conflictId);
    }

    private ConflictCase requireOpen(String conflictId) {
        ConflictCase row = conflictCaseMapper.selectById(conflictId);
        if (row == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND", "Conflict not found: " + conflictId);
        }
        if (row.getStatus() != ConflictStatus.OPEN) {
            throw new BusinessException("CONFLICT_NOT_OPEN", "Conflict is not open: " + conflictId);
        }
        return row;
    }
}
