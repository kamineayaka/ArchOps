package com.archops.conflict.service;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.ConflictEventType;
import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.conflict.dto.OpenOperationPlanResponse;
import com.archops.conflict.mapper.ConflictCaseMapper;
import com.archops.curated.domain.CuratedFact;
import com.archops.observed.domain.ObservedFact;
import com.archops.user.domain.PlatformRole;
import com.archops.user.domain.PlatformUser;
import com.archops.user.security.AuthUserPrincipal;
import com.archops.user.service.UserLookupService;
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
    private final ConflictEventService conflictEventService;
    private final UserLookupService userLookupService;
    private final TransactionTemplate requiresNewTx;

    public ConflictCollaborationService(
            ConflictCaseMapper conflictCaseMapper,
            ConflictDetectionService conflictDetectionService,
            ConflictEventService conflictEventService,
            UserLookupService userLookupService,
            PlatformTransactionManager transactionManager
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDetectionService = conflictDetectionService;
        this.conflictEventService = conflictEventService;
        this.userLookupService = userLookupService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        conflictEventService.append(conflictId, ConflictEventType.ACKNOWLEDGED, actor.getUserId(), Map.of(
                "via", "claim"
        ));
        conflictEventService.append(conflictId, ConflictEventType.HANDLER_ACCEPTED, actor.getUserId(), Map.of(
                "via", "claim"
        ));
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
        conflictEventService.append(conflictId, ConflictEventType.ACKNOWLEDGED, actor.getUserId(), Map.of(
                "via", "acknowledge"
        ));
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
            conflictEventService.append(conflictId, ConflictEventType.ACKNOWLEDGED, actor.getUserId(), Map.of(
                    "via", "acknowledge_and_self_appoint"
            ));
            conflictEventService.append(conflictId, ConflictEventType.HANDLER_ACCEPTED, actor.getUserId(), Map.of(
                    "via", "acknowledge_and_self_appoint"
            ));
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
        conflictEventService.append(conflictId, ConflictEventType.HANDLER_ACCEPTED, actor.getUserId(), Map.of(
                "via", "self_appoint"
        ));
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * 归属方（高级角色）指派一般角色为待接受冲突处理人。
     * 已有处理人（待接受/已接受）时不可强行改派。
     */
    @Transactional
    public ConflictCaseResponse assignHandler(String conflictId, String assigneeUserId, AuthUserPrincipal actor) {
        requireRole(actor, PlatformRole.SENIOR, "CONFLICT_ASSIGN_ROLE_DENIED",
                "Only 高级角色 may assign a conflict handler");
        ConflictCase row = requireOpen(conflictId);
        if (!Boolean.TRUE.equals(row.getAcknowledged()) || row.getOwnerUserId() == null) {
            throw new BusinessException("CONFLICT_NOT_ACKNOWLEDGED",
                    "Conflict must be 已知悉 with 冲突归属 before assign");
        }
        if (!actor.getUserId().equals(row.getOwnerUserId())) {
            throw new BusinessException("CONFLICT_NOT_OWNER",
                    "Only the 冲突归属方 may assign a handler");
        }
        if (row.getHandlerAcceptance() != HandlerAcceptance.NONE || row.getHandlerUserId() != null) {
            throw new BusinessException("CONFLICT_HANDLER_EXISTS",
                    "Cannot reassign while a handler is pending or accepted; handler must reject/transfer or finish");
        }
        PlatformUser assignee = requireGeneralUser(assigneeUserId, "CONFLICT_ASSIGNEE_INVALID");
        if (assignee.getId().equals(actor.getUserId())) {
            throw new BusinessException("CONFLICT_ASSIGNEE_INVALID",
                    "Use self-appoint instead of assigning yourself");
        }

        Instant now = Instant.now();
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, row.getId())
                .set(ConflictCase::getHandlerUserId, assignee.getId())
                .set(ConflictCase::getHandlerAcceptance, HandlerAcceptance.PENDING_ACCEPT)
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(conflictId, ConflictEventType.HANDLER_ASSIGNED, actor.getUserId(), Map.of(
                "assigneeUserId", assignee.getId(),
                "ownerUserId", row.getOwnerUserId()
        ));
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * 待接受处理人接受指派/转让 → 已接受处理人.
     */
    @Transactional
    public ConflictCaseResponse acceptHandler(String conflictId, AuthUserPrincipal actor) {
        ConflictCase row = requireOpen(conflictId);
        requirePendingHandler(row, actor);
        Instant now = Instant.now();
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, row.getId())
                .set(ConflictCase::getHandlerAcceptance, HandlerAcceptance.ACCEPTED)
                .set(ConflictCase::getUpdatedAt, now));
        conflictEventService.append(conflictId, ConflictEventType.HANDLER_ACCEPTED, actor.getUserId(), Map.of(
                "via", "accept_assignment"
        ));
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * 待接受处理人拒绝（须理由）→ 无处理人、归属不变、仍已知悉.
     */
    @Transactional
    public ConflictCaseResponse rejectHandler(String conflictId, String reason, AuthUserPrincipal actor) {
        ConflictCase row = requireOpen(conflictId);
        requirePendingHandler(row, actor);
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("HANDLER_REJECT_REASON_REQUIRED",
                    "拒绝指派必须说明理由");
        }
        String ownerUserId = row.getOwnerUserId();
        Instant now = Instant.now();
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, row.getId())
                .set(ConflictCase::getHandlerUserId, null)
                .set(ConflictCase::getHandlerAcceptance, HandlerAcceptance.NONE)
                .set(ConflictCase::getUpdatedAt, now));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", trimmed);
        detail.put("ownerUserId", ownerUserId);
        conflictEventService.append(conflictId, ConflictEventType.HANDLER_REJECTED, actor.getUserId(), detail);
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * 当前处理人（待接受或已接受）转让给另一一般角色；归属不变；拟接手人进入待接受.
     */
    @Transactional
    public ConflictCaseResponse transferHandler(String conflictId, String toUserId, AuthUserPrincipal actor) {
        ConflictCase row = requireOpen(conflictId);
        if (row.getHandlerUserId() == null
                || (row.getHandlerAcceptance() != HandlerAcceptance.PENDING_ACCEPT
                && row.getHandlerAcceptance() != HandlerAcceptance.ACCEPTED)) {
            throw new BusinessException("CONFLICT_NOT_HANDLER",
                    "Only the current 冲突处理人 may transfer the handler role");
        }
        if (!actor.getUserId().equals(row.getHandlerUserId())) {
            throw new BusinessException("CONFLICT_NOT_HANDLER",
                    "Only the current 冲突处理人 may transfer the handler role");
        }
        PlatformUser recipient = requireGeneralUser(toUserId, "CONFLICT_TRANSFER_TARGET_INVALID");
        if (recipient.getId().equals(actor.getUserId())) {
            throw new BusinessException("CONFLICT_TRANSFER_TARGET_INVALID",
                    "Cannot transfer handler role to yourself");
        }
        String previousHandlerId = row.getHandlerUserId();
        String previousAcceptance = row.getHandlerAcceptance().name();
        Instant now = Instant.now();
        conflictCaseMapper.update(null, new LambdaUpdateWrapper<ConflictCase>()
                .eq(ConflictCase::getId, row.getId())
                .set(ConflictCase::getHandlerUserId, recipient.getId())
                .set(ConflictCase::getHandlerAcceptance, HandlerAcceptance.PENDING_ACCEPT)
                .set(ConflictCase::getUpdatedAt, now));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromUserId", previousHandlerId);
        detail.put("toUserId", recipient.getId());
        detail.put("fromAcceptance", previousAcceptance);
        detail.put("ownerUserId", row.getOwnerUserId());
        conflictEventService.append(conflictId, ConflictEventType.HANDLER_TRANSFER_OFFERED, actor.getUserId(), detail);
        return conflictDetectionService.getById(conflictId);
    }

    /**
     * Gate for opening an operation plan (full plan machine is ticket 07).
     * Only the 已接受冲突处理人 may pass. 待接受 cannot open plans.
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

    /**
     * Confirm close while tracks remain equal. Race drift fails without closing.
     */
    @Transactional
    public ConflictCaseResponse confirmClose(String conflictId, AuthUserPrincipal actor) {
        ConflictCase row = conflictCaseMapper.selectById(conflictId);
        if (row == null) {
            throw new BusinessException("CONFLICT_NOT_FOUND", "Conflict not found: " + conflictId);
        }
        if (row.getStatus() != ConflictStatus.PENDING_CLOSE) {
            throw new BusinessException("CONFLICT_NOT_PENDING_CLOSE",
                    "Only 待确认关闭 conflicts can be confirmed closed");
        }
        boolean acceptedHandler = row.getHandlerAcceptance() == HandlerAcceptance.ACCEPTED
                && actor.getUserId().equals(row.getHandlerUserId());
        if (!acceptedHandler) {
            throw new BusinessException("CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER",
                    "Only the 已接受冲突处理人 may confirm close");
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
                conflictEventService.append(conflictId, ConflictEventType.CONFIRM_FAILED, actor.getUserId(), detail);
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
        conflictEventService.append(conflictId, ConflictEventType.CLOSED, actor.getUserId(), Map.of(
                "curatedTargetId", curated.getTargetId(),
                "observedTargetId", observed.getTargetId()
        ));
        return conflictDetectionService.getById(conflictId);
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

    private static void requirePendingHandler(ConflictCase row, AuthUserPrincipal actor) {
        if (row.getHandlerAcceptance() != HandlerAcceptance.PENDING_ACCEPT
                || row.getHandlerUserId() == null) {
            throw new BusinessException("CONFLICT_NOT_PENDING_HANDLER",
                    "No 待接受冲突处理人 on this conflict");
        }
        if (!actor.getUserId().equals(row.getHandlerUserId())) {
            throw new BusinessException("CONFLICT_NOT_PENDING_HANDLER",
                    "Only the 待接受冲突处理人 may accept or reject this assignment");
        }
    }

    private PlatformUser requireGeneralUser(String userId, String invalidCode) {
        PlatformUser user = userLookupService.findById(userId)
                .orElseThrow(() -> new BusinessException(invalidCode, "User not found: " + userId));
        if (user.getRole() != PlatformRole.GENERAL) {
            throw new BusinessException(invalidCode,
                    "Conflict handler must be a 一般角色 user");
        }
        return user;
    }

    private static void requireRole(AuthUserPrincipal actor, PlatformRole expected, String code, String message) {
        if (actor.getRole() != expected) {
            throw new BusinessException(code, message);
        }
    }
}
