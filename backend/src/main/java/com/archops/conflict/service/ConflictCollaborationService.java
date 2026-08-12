package com.archops.conflict.service;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.dto.OpenOperationPlanResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.user.domain.PlatformRole;
import com.archops.user.security.AuthUserPrincipal;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Conflict collaboration Must path (ticket 05): 认领 / 已知悉+自任 / plan-open gate.
 * Assign / reject / transfer are ticket 11.
 */
@Service
public class ConflictCollaborationService {

    private final ConflictCaseMapper conflictCaseMapper;
    private final ConflictDetectionService conflictDetectionService;

    public ConflictCollaborationService(
            ConflictCaseMapper conflictCaseMapper,
            ConflictDetectionService conflictDetectionService
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDetectionService = conflictDetectionService;
    }

    /**
     * 一般角色认领：尚未已知悉 → 已知悉 + 冲突归属 + 已接受处理人.
     */
    @Transactional
    public ConflictCaseResponse claim(String conflictId, AuthUserPrincipal actor) {
        requireRole(actor, PlatformRole.GENERAL, "CONFLICT_CLAIM_ROLE_DENIED",
                "Only 一般角色 may claim an unacknowledged conflict");
        ConflictCase row = requireOpen(conflictId);
        if (Boolean.TRUE.equals(row.getAcknowledged()) || row.getOwnerUserId() != null) {
            throw new BusinessException("CONFLICT_ALREADY_OWNED",
                    "Cannot claim a conflict that already has 冲突归属");
        }
        Instant now = Instant.now();
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, row.getId())
                .set(ConflictCase::getAcknowledged, true)
                .set(ConflictCase::getAcknowledgedAt, now)
                .set(ConflictCase::getOwnerUserId, actor.getUserId())
                .set(ConflictCase::getHandlerUserId, actor.getUserId())
                .set(ConflictCase::getHandlerAcceptance, HandlerAcceptance.ACCEPTED)
                .set(ConflictCase::getUpdatedAt, now));
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * 高级角色已知悉（取得冲突归属），暂不设处理人.
     */
    @Transactional
    public ConflictCaseResponse acknowledge(String conflictId, AuthUserPrincipal actor) {
        requireRole(actor, PlatformRole.SENIOR, "CONFLICT_ACK_ROLE_DENIED",
                "Only 高级角色 may acknowledge without claiming as handler");
        ConflictCase row = requireOpen(conflictId);
        if (Boolean.TRUE.equals(row.getAcknowledged()) || row.getOwnerUserId() != null) {
            throw new BusinessException("CONFLICT_ALREADY_OWNED",
                    "Conflict is already 已知悉 with 冲突归属");
        }
        Instant now = Instant.now();
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, row.getId())
                .set(ConflictCase::getAcknowledged, true)
                .set(ConflictCase::getAcknowledgedAt, now)
                .set(ConflictCase::getOwnerUserId, actor.getUserId())
                .set(ConflictCase::getUpdatedAt, now));
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * 高级角色已知悉并自任为已接受冲突处理人（Must 路径）.
     */
    @Transactional
    public ConflictCaseResponse acknowledgeAndSelfAppoint(String conflictId, AuthUserPrincipal actor) {
        requireRole(actor, PlatformRole.SENIOR, "CONFLICT_SELF_APPOINT_ROLE_DENIED",
                "Only 高级角色 may acknowledge and self-appoint as handler");
        ConflictCase row = requireOpen(conflictId);
        Instant now = Instant.now();

        if (!Boolean.TRUE.equals(row.getAcknowledged())) {
            conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                    .eq(ConflictCase::getId, row.getId())
                    .set(ConflictCase::getAcknowledged, true)
                    .set(ConflictCase::getAcknowledgedAt, now)
                    .set(ConflictCase::getOwnerUserId, actor.getUserId())
                    .set(ConflictCase::getHandlerUserId, actor.getUserId())
                    .set(ConflictCase::getHandlerAcceptance, HandlerAcceptance.ACCEPTED)
                    .set(ConflictCase::getUpdatedAt, now));
            return conflictDetectionService.getById(conflictId);
        }

        if (!actor.getUserId().equals(row.getOwnerUserId())) {
            throw new BusinessException("CONFLICT_NOT_OWNER",
                    "Only the 冲突归属方 may self-appoint as handler");
        }
        if (row.getHandlerUserId() != null && row.getHandlerAcceptance() != HandlerAcceptance.NONE) {
            throw new BusinessException("CONFLICT_HANDLER_EXISTS",
                    "Conflict already has a handler");
        }
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, row.getId())
                .set(ConflictCase::getHandlerUserId, actor.getUserId())
                .set(ConflictCase::getHandlerAcceptance, HandlerAcceptance.ACCEPTED)
                .set(ConflictCase::getUpdatedAt, now));
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * Gate for opening an operation plan (full plan machine is ticket 07).
     * Only the 已接受冲突处理人 may pass.
     */
    @Transactional(readOnly = true)
    public OpenOperationPlanResponse openOperationPlan(String conflictId, AuthUserPrincipal actor) {
        ConflictCase row = requireOpen(conflictId);
        boolean acceptedHandler = row.getHandlerAcceptance() == HandlerAcceptance.ACCEPTED
                && actor.getUserId().equals(row.getHandlerUserId());
        if (!acceptedHandler) {
            throw new BusinessException("PLAN_REQUIRES_ACCEPTED_HANDLER",
                    "Only the 已接受冲突处理人 may open an operation plan for this conflict");
        }
        return new OpenOperationPlanResponse(
                conflictId,
                "OPEN_INTENT_ACCEPTED",
                actor.getUserId(),
                "Accepted handler may proceed to plan generation (ticket 07)"
        );
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

    private static void requireRole(AuthUserPrincipal actor, PlatformRole expected, String code, String message) {
        if (actor.getRole() != expected) {
            throw new BusinessException(code, message);
        }
    }
}
