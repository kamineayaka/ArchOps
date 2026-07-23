package com.archops.knowledge.architecture.event;

import org.springframework.context.ApplicationEvent;

/** Published after an architecture proposal is merged into SSOT. */
public class ArchitectureMergedEvent extends ApplicationEvent {

    private final String partitionKey;
    private final Long version;
    private final Long proposalId;

    public ArchitectureMergedEvent(String partitionKey, Long version, Long proposalId) {
        super(partitionKey != null ? partitionKey : "unknown");
        this.partitionKey = partitionKey;
        this.version = version;
        this.proposalId = proposalId;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public Long getVersion() {
        return version;
    }

    public Long getProposalId() {
        return proposalId;
    }

    /** Alias for listeners that expect revision version as revisionId. */
    public Long getRevisionId() {
        return version;
    }
}
