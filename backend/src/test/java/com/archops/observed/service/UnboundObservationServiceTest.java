package com.archops.observed.service;

import com.archops.agent.dto.AgentHeartbeatResponse;
import com.archops.observed.domain.UnboundObservationCandidate;
import com.archops.observed.domain.UnboundReason;
import com.archops.observed.dto.UnboundCandidateResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnboundObservationServiceTest {

    @Test
    void hostRuntimeKeyJoinsWithNulSeparator() {
        assertThat(UnboundObservationService.hostRuntimeKey("host-1", "rt-a"))
                .isEqualTo("host-1\0rt-a");
    }

    @Test
    void heartbeatSummaryUsesReasonNameAndDoesNotPromiseUpgradeChain() {
        UnboundObservationCandidate row = candidate(
                "unb-1", "rt-9", "orphan", UnboundReason.MISSING_LABEL, false);

        AgentHeartbeatResponse.UnboundCandidate summary = UnboundObservationService.toHeartbeatSummary(row);

        assertThat(summary).isEqualTo(new AgentHeartbeatResponse.UnboundCandidate(
                "unb-1", "MISSING_LABEL", "rt-9", "orphan", false));
    }

    @Test
    void listItemCarriesLabelsReasonAndObservedAt() {
        Instant at = Instant.parse("2026-09-12T11:00:00Z");
        UnboundObservationCandidate row = candidate(
                "unb-2", "rt-2", "app", UnboundReason.UNKNOWN_OBJECT_ID, true);
        row.setSourceAgentId("agent-1");
        row.setSourceHostId("host-1");
        row.setObservedAt(at);

        UnboundCandidateResponse response = UnboundObservationService.toListItem(
                row, Map.of("archops.object_id", "obj-x"));

        assertThat(response).isEqualTo(new UnboundCandidateResponse(
                "unb-2",
                "agent-1",
                "host-1",
                "rt-2",
                "app",
                Map.of("archops.object_id", "obj-x"),
                UnboundReason.UNKNOWN_OBJECT_ID,
                true,
                at
        ));
    }

    @Test
    void listDropsCandidatesWhoseHostRuntimeIsConsumed() {
        UnboundObservationCandidate open = candidate(
                "unb-open", "rt-open", "open", UnboundReason.MISSING_LABEL, false);
        open.setSourceHostId("host-1");
        UnboundObservationCandidate consumed = candidate(
                "unb-used", "rt-used", "used", UnboundReason.MISSING_LABEL, false);
        consumed.setSourceHostId("host-1");
        Set<String> consumedKeys = Set.of(UnboundObservationService.hostRuntimeKey("host-1", "rt-used"));

        List<UnboundObservationCandidate> kept = UnboundObservationService.excludeConsumed(
                List.of(open, consumed), consumedKeys);

        assertThat(kept).containsExactly(open);
    }

    private static UnboundObservationCandidate candidate(
            String id,
            String runtimeId,
            String name,
            UnboundReason reason,
            boolean upgradeChainPromised
    ) {
        UnboundObservationCandidate row = new UnboundObservationCandidate();
        row.setId(id);
        row.setRuntimeId(runtimeId);
        row.setName(name);
        row.setReason(reason);
        row.setUpgradeChainPromised(upgradeChainPromised);
        return row;
    }
}
