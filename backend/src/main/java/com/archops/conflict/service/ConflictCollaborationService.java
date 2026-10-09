package com.archops.conflict.service;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.AcceptedHandlerPolicy;
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
    private final ConflictCaseAssembler conflictCaseAssembler;
    private final ConflictEventService conflictEventService;
    private final UserLookupService userLookupService;
    private final TransactionTemplate requiresNewTx;

    public ConflictCollaborationService(
            ConflictCaseMapper conflictCaseMapper,
            ConflictDetectionService conflictDetectionService,
            ConflictCaseAssembler conflictCaseAssembler,
            ConflictEventService conflictEventService,
            UserLookupService userLookupService,
            PlatformTransactionManager transactionManager
    ) {
        this.conflictCaseMapper = conflictCaseMapper;
        this.conflictDetectionService = conflictDetectionService;
        this.conflictCaseAssembler = conflictCaseAssembler;
        this.conflictEventService = conflictEventService;
        this.userLookupService = userLookupService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 认领不再写入已知悉、归属或处理人.
     */
    @Transactional(readOnly = true)
    public ConflictCaseResponse claim(String conflictId) {
        requireOpen(conflictId);
        return conflictCaseAssembler.getById(conflictId);
    }

    /**
     * 已知悉不再写入归属或处理人.
     */
    @Transactional(readOnly = true)
    public ConflictCaseResponse acknowledge(String conflictId) {
        requireOpen(conflictId);
        return conflictCaseAssembler.getById(conflictId);
    }

    /**
     * 自任不再写入已知悉或处理人.
     */
    @Transactional(readOnly = true)
    public ConflictCaseResponse acknowledgeAndSelfAppoint(String conflictId) {
        requireOpen(conflictId);
        return conflictCaseAssembler.getById(conflictId);
    }

    /**
     * 指派不再写入处理人.
     */
    @Transactional(readOnly = true)
    public ConflictCaseResponse assignHandler(String conflictId) {
        requireOpen(conflictId);
        return conflictCaseAssembler.getById(conflictId);
    }

    /**
     * 接受不再写入处理人.
     */
    @Transactional(readOnly = true)
    public ConflictCaseResponse acceptHandler(String conflictId) {
        requireOpen(conflictId);
        return conflictCaseAssembler.getById(conflictId);
    }

    /**
     * 拒绝不再写入处理人.
     */
    @Transactional(readOnly = true)
    public ConflictCaseResponse rejectHandler(String conflictId) {
        requireOpen(conflictId);
        return conflictCaseAssembler.getById(conflictId);
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
        return conflictCaseAssembler.getById(conflictId);
    }

    /**
     * Gate for opening an operation plan (full plan machine is ticket 07).
     * Only the 已接受冲突处理人 may pass. 待接受 cannot open plans.
     */
    @Transactional(readOnly = true)
    public OpenOperationPlanResponse openOperationPlan(String conflictId, AuthUserPrincipal actor) {
        ConflictCase row = requireOpen(conflictId);
        AcceptedHandlerPolicy.require(row, actor, "PLAN_REQUIRES_ACCEPTED_HANDLER",
                "Only the 已接受冲突处理人 may open an operation plan for this conflict");
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
