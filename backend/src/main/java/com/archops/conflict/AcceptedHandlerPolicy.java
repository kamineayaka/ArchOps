package com.archops.conflict;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.user.security.AuthUserPrincipal;

/**
 * Frozen collaboration gate: only the 已接受冲突处理人 may proceed.
 * Predicate is {@code HandlerAcceptance.ACCEPTED} and {@code actor.userId}
 * equals {@code handlerUserId}. Callers supply the HTTP code so plan
 * operations stay {@code PLAN_REQUIRES_ACCEPTED_HANDLER} and confirm-close
 * stays {@code CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER}.
 */
public final class AcceptedHandlerPolicy {

    private AcceptedHandlerPolicy() {
    }

    public static void require(
            ConflictCase conflict,
            AuthUserPrincipal actor,
            String errorCode,
            String message
    ) {
        boolean ok = conflict.getHandlerAcceptance() == HandlerAcceptance.ACCEPTED
                && actor.getUserId().equals(conflict.getHandlerUserId());
        if (!ok) {
            throw new BusinessException(errorCode, message);
        }
    }
}
