package com.archops.approval.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archops.approval.domain.RiskLevel;
import com.archops.user.domain.ApprovalPolicy;
import com.archops.user.domain.RbacTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalGateTest {

    private ApprovalGate gate;

    @BeforeEach
    void setUp() {
        gate = new ApprovalGate();
    }

    @Test
    void lowTierAlwaysRequiresManualApproval() {
        ApprovalGate.Decision decision = gate.decide(RbacTier.LOW, ApprovalPolicy.AUTO_C, RiskLevel.LOW);
        assertFalse(decision.autoExecute());
    }

    @Test
    void midTierCannotUseAutoC() {
        ApprovalGate.Decision lowRisk = gate.decide(RbacTier.MID, ApprovalPolicy.AUTO_C, RiskLevel.LOW);
        assertTrue(lowRisk.autoExecute());

        ApprovalGate.Decision mediumRisk = gate.decide(RbacTier.MID, ApprovalPolicy.AUTO_C, RiskLevel.MEDIUM);
        assertFalse(mediumRisk.autoExecute());
    }

    @Test
    void riskBasedBAutosOnlyLow() {
        assertTrue(gate.decide(RbacTier.HIGH, ApprovalPolicy.RISK_BASED_B, RiskLevel.LOW).autoExecute());
        assertFalse(gate.decide(RbacTier.HIGH, ApprovalPolicy.RISK_BASED_B, RiskLevel.MEDIUM).autoExecute());
        assertFalse(gate.decide(RbacTier.HIGH, ApprovalPolicy.RISK_BASED_B, RiskLevel.HIGH).autoExecute());
    }

    @Test
    void autoCRequiresApprovalOnlyForHigh() {
        assertTrue(gate.decide(RbacTier.HIGH, ApprovalPolicy.AUTO_C, RiskLevel.LOW).autoExecute());
        assertTrue(gate.decide(RbacTier.HIGH, ApprovalPolicy.AUTO_C, RiskLevel.MEDIUM).autoExecute());
        assertFalse(gate.decide(RbacTier.HIGH, ApprovalPolicy.AUTO_C, RiskLevel.HIGH).autoExecute());
    }

    @Test
    void manualAAlwaysGates() {
        assertFalse(gate.decide(RbacTier.HIGH, ApprovalPolicy.MANUAL_A, RiskLevel.LOW).autoExecute());
    }
}
