package com.archops.conflict;

import com.archops.common.exception.BusinessException;
import com.archops.conflict.domain.ConflictCase;
import com.archops.conflict.domain.HandlerAcceptance;
import com.archops.user.domain.PlatformRole;
import com.archops.user.security.AuthUserPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceptedHandlerPolicyTest {

    @Test
    void pendingAcceptThrowsPlanRequiresAcceptedHandler() {
        ConflictCase conflict = conflict("user-handler", HandlerAcceptance.PENDING_ACCEPT);
        AuthUserPrincipal actor = actor("user-handler");

        assertThatThrownBy(() -> AcceptedHandlerPolicy.require(
                        conflict,
                        actor,
                        "PLAN_REQUIRES_ACCEPTED_HANDLER",
                        "Only the 已接受冲突处理人 may open an operation plan for this conflict"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only the 已接受冲突处理人 may open an operation plan for this conflict")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_REQUIRES_ACCEPTED_HANDLER");
    }

    @Test
    void noneHandlerThrowsPlanRequiresAcceptedHandler() {
        ConflictCase conflict = conflict(null, HandlerAcceptance.NONE);
        AuthUserPrincipal actor = actor("user-other");

        assertThatThrownBy(() -> AcceptedHandlerPolicy.require(
                        conflict,
                        actor,
                        "PLAN_REQUIRES_ACCEPTED_HANDLER",
                        "Only the 已接受冲突处理人 may open an operation plan for this conflict"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only the 已接受冲突处理人 may open an operation plan for this conflict")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_REQUIRES_ACCEPTED_HANDLER");
    }

    @Test
    void acceptedButActorIsNotHandlerThrowsPlanRequiresAcceptedHandler() {
        ConflictCase conflict = conflict("user-handler", HandlerAcceptance.ACCEPTED);
        AuthUserPrincipal actor = actor("user-other");

        assertThatThrownBy(() -> AcceptedHandlerPolicy.require(
                        conflict,
                        actor,
                        "PLAN_REQUIRES_ACCEPTED_HANDLER",
                        "Only the 已接受冲突处理人 may select a branch or manage the operation plan"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only the 已接受冲突处理人 may select a branch or manage the operation plan")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_REQUIRES_ACCEPTED_HANDLER");
    }

    @Test
    void pendingAcceptKeepsCallerSuppliedConfirmCloseCode() {
        ConflictCase conflict = conflict("user-handler", HandlerAcceptance.PENDING_ACCEPT);
        AuthUserPrincipal actor = actor("user-handler");

        assertThatThrownBy(() -> AcceptedHandlerPolicy.require(
                        conflict,
                        actor,
                        "CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER",
                        "Only the 已接受冲突处理人 may confirm close"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only the 已接受冲突处理人 may confirm close")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER");
    }

    @Test
    void acceptedHandlerSelfDoesNotThrow() {
        ConflictCase conflict = conflict("user-handler", HandlerAcceptance.ACCEPTED);
        AuthUserPrincipal actor = actor("user-handler");

        assertThatCode(() -> AcceptedHandlerPolicy.require(
                        conflict,
                        actor,
                        "PLAN_REQUIRES_ACCEPTED_HANDLER",
                        "Only the 已接受冲突处理人 may open an operation plan for this conflict"))
                .doesNotThrowAnyException();
    }

    private static ConflictCase conflict(String handlerUserId, HandlerAcceptance acceptance) {
        ConflictCase row = new ConflictCase();
        row.setHandlerUserId(handlerUserId);
        row.setHandlerAcceptance(acceptance);
        return row;
    }

    private static AuthUserPrincipal actor(String userId) {
        return new AuthUserPrincipal(userId, userId, PlatformRole.GENERAL);
    }
}
