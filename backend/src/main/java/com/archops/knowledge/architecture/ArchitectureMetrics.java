package com.archops.knowledge.architecture;

import com.archops.knowledge.architecture.domain.ProposalStatus;
import com.archops.knowledge.architecture.repository.ArchitectureProposalRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Architecture product metrics (ML-8-05).
 */
@Component
public class ArchitectureMetrics {

    private final Counter mergedTotal;
    private final Counter autoMergeTotal;
    private final Counter rollbackTotal;

    public ArchitectureMetrics(MeterRegistry meterRegistry, ArchitectureProposalRepository proposalRepository) {
        Gauge.builder(
                        "archops_architecture_proposals_pending",
                        proposalRepository,
                        repo -> repo.countByStatus(ProposalStatus.PENDING_REVIEW))
                .description("Architecture proposals awaiting review")
                .register(meterRegistry);

        this.mergedTotal = Counter.builder("archops_architecture_merged_total")
                .description("Architecture proposals merged into SSOT")
                .register(meterRegistry);
        this.autoMergeTotal = Counter.builder("archops_architecture_auto_merge_total")
                .description("Architecture proposals auto-merged")
                .register(meterRegistry);
        this.rollbackTotal = Counter.builder("archops_architecture_rollback_total")
                .description("Architecture partition rollbacks")
                .register(meterRegistry);
    }

    public void incrementMerged() {
        mergedTotal.increment();
    }

    public void incrementAutoMerge() {
        autoMergeTotal.increment();
    }

    public void incrementRollback() {
        rollbackTotal.increment();
    }
}
