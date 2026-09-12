package com.archops.conflict;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.user.security.AuthUserPrincipal;

/**
 * Single 已接受处理人 gate. Callers keep distinct HTTP codes and per-site messages.
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
            throw new BusinessException("PLAN_REQUIRES_ACCEPTED_HANDLER", message);
        }
    }
}
