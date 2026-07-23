package com.archops.knowledge.indexing;

import com.archops.knowledge.architecture.event.ArchitectureMergedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ArchitectureMergedIndexListener {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureMergedIndexListener.class);

    private final KnowledgeIndexingService indexingService;

    public ArchitectureMergedIndexListener(KnowledgeIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @EventListener
    public void onMerged(ArchitectureMergedEvent event) {
        if (event == null || event.getPartitionKey() == null) {
            return;
        }
        log.info("Reindexing partition after merge: {}", event.getPartitionKey());
        indexingService.scheduleReindexPartition(event.getPartitionKey());
    }
}
